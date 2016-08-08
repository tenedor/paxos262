package com.github.tenedor.paxos;

import java.rmi.*;
import java.rmi.server.*;
import java.util.*;
import java.util.concurrent.locks.*;
import java.security.SecureRandom;
import java.math.BigInteger;

public class PaxosDbImpl extends UnicastRemoteObject implements PaxosDb {

  // State
  // -----

  /**
   * The uid of this server. It will always begin with {@code paxosdb_} and will
   * be unique with probability almost 1.
   */  
  public String uid;

  /**
   * The {@code paxosId} of the oldest ballot in the ledger.
   * <p>
   * Currently there is no checkpointing and this will always be 0.
   */  
  private int ledgerBottomId = 0;

  /**
   * The {@code paxosId} of the newest ballot in the ledger.
   */  
  private int ledgerTopId = -1;

  // TODO: initialize
  /**
   * The Paxos ledger for values from {@link #ledgerBottomId} to
   * {@link #ledgerTopId}. A successful ballot is added to the ledger once every
   * ballot with a lower {@code paxosId} is already in the ledger.
   */  
  private List<LedgerEntry> ledger;

  // TODO: set a larger minimum size?
  /**
   * A local copy of the database. This progresses in step with the ledger. It
   * is thread-safe, so reads don't need to acquire a lock. Writes must acquire
   * a lock to ensure the canonical ordering of the state machine.
   * <p>
   * Currently there is no checkpointing, though this may change in the future.
   * When checkpointing is added the database semantics will get more
   * complicated.
   */  
  private Hashtable<String, String> localDb = new Hashtable<String, String>();

  /**
   * The ids of all fulfilled update requests. Client and server requests with
   * already-fulfilled ids return immediately. Passed requests that have already
   * been fulfilled are added to the ledger but do not update {@link #localDb}.
   */
  private Set<Integer> fulfilledRequestIds = new HashSet<Integer>();

  /**
   * The current leadership era. The era increases each time a leader election
   * begins or resolves.
   */  
  private LeaderEra leaderEra = new LeaderEra(0, "");

  /**
   * Ballots accepted by this server with {@code paxosId}s higher than
   * {@link #ledgerTopId}. If this server is the leader,
   * {@link #liveLeaderBallots} is used instead.
   */
  private Map<Integer, PaxosState> liveBallots =
      new HashMap<Integer, PaxosState>();

  /**
   * Ballots led by this server with {@code paxosId}s higher than
   * {@link #ledgerTopId}. If this server is not the leader,
   * {@link #liveBallots} is used instead.
   */
  private Map<Integer, PaxosLeaderState> liveLeaderBallots =
      new HashMap<Integer, PaxosLeaderState>();

  // TODO: how big should the initial capacity be?
  /**
   * The set of values to pass, keyed by request id. This is only used by the
   * server in its role as leader. When a request is selected by a Paxos
   * instance it is moved to the {@code #valuesBeingPassed} map. Value requests
   * are accessible both by LRU-ordered iteration and by key.
   */
  private LinkedHashMap<Integer, PaxosValueRequest> valueRequests =
      new LinkedHashMap<Integer, PaxosValueRequest>(100, 0.75f, true);

  /**
   * The set of values currently being passed by Paxos instances, keyed by
   * request id. This is only used by the server in its role as leader.
   */
  private Map<Integer, PaxosValueRequest> valuesBeingPassed =
      new HashMap<Integer, PaxosValueRequest>();

  /**
   * The current set of legislators.
   */
  private Map<String, PaxosDb> legislators = new HashMap<String, PaxosDb>();
  
  /**
   * A lock for reading or writing paxos state. Methods that must be called with
   * this lock held have the suffix "LP".
   */
  private final Lock paxosRWLock = new ReentrantLock();

  /**
   * A condition that is signalAll'ed each time a leader election resolves.
   * Methods can wait on this condition to be woken up when a leader is elected.
   */
  private final Condition leaderElected = paxosRWLock.newCondition();

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = -4840150450504062218L;


  // Constructors
  // ------------

  /**
   * Create a PaxosDbImpl object.
   */  
  public PaxosDbImpl() throws RemoteException {
    // Use a 6-character hash for generating uids.
    //
    // The probability that 100 machines fail to pick mutually unique hashes is
    // 1e-7. Short hashes are easier to read so this is nice for debugging, and
    // this is good enough for testing.
    String randomHash = new BigInteger(30, new SecureRandom()).toString(32);
    uid = "paxosdb_" + randomHash;
  }

  /**
   * Create a PaxosDbImpl object with a specified id. The server's {@code uid}
   * will be this id appended to the prefix {@code paxosdb_}.
   *
   * @param id the id to use
   */  
  public PaxosDbImpl(String id) throws RemoteException {
    uid = "paxosdb_" + id;
  }


  // Ledger and Database
  // -------------------
  
  /**
   * Add passed ballots to the ledger and database.
   */
  private void updateLedger() {
    synchronized(paxosRWLock) {
      while (true) {
        // TODO - handle the case where this machine is the leader
        
        // loop until the next paxos value has not yet been passed
        PaxosState s = liveBallots.get(ledgerTopId + 1);
        if (s == null || !s.hasSucceeded) {
          break;
        }
        
        // confirm that the state machine is progressing in lockstep
        assert(ledger.size() == ledgerTopId - ledgerBottomId + 1);
        assert(ledgerTopId + 1 == s.paxosId);
        
        // construct the next ledger entry
        int previousHash = ledger.get(ledgerTopId).cumulativeHash;
        LedgerEntry entry = new LedgerEntry(s.value, previousHash);
        
        // add the ledger entry
        ledger.add(entry);
        
        // update the database, unless this update has already been applied
        if (!fulfilledRequestIds.contains(entry.value.requestId)) {
          localDb.put(entry.value.key, entry.value.value);
          fulfilledRequestIds.add(entry.value.requestId);
        }

        // update the ledger top id
        ledgerTopId++;
      }
    }
  }


  // Client Handling
  // ---------------

  @Override
  public boolean clientRequest(PaxosValue value)
      throws BusyReplicaException, RemoteException {
    synchronized (paxosRWLock) {
      // return if this request has already been fulfilled
      if (fulfilledRequestIds.contains(value.requestId)) {
        return true;
      }

      // reject this request if this server is currently the leader - tell the
      // client to switch replicas
      if (isLeaderLP()) {
        throw new BusyReplicaException();
      }
    }

    /**
     * TODO:
     *   * This thread should add itself to a registry. This entry likely
     *     includes thread id, creation timestamp, era number, and request id.
     *     
     *   * A system thread should watch the registry for unresolved requests
     *     that have been around a long time and other signs that a new leader
     *     is needed.
     *     
     *   * This thread should be interrupted if it is still waiting on an old
     *     era when a new leader is elected: the old leader may be slowed or
     *     stopped and this thread could hang a long time. Java RMI doesn't
     *     allow interruptions, but the Interruptible RMI library may be useful.
     *     
     *   * This call should eventually timeout and throw a TimeoutException.
     */

    PaxosDb leader;
    
    while (true) {
      // get the leader, waiting for an election result if necessary
      synchronized (paxosRWLock) {
        leader = currentLeaderLP();
        while(leader == null) {
          leaderElected.awaitUninterruptibly();
          leader = currentLeaderLP();
        }
      }

      // request that the leader pass this value
      try {
        boolean success = leader.requestValue(value);

        // success: remove thread id from registry and return true
        if (success) {
          return true;
        }

      // leadership exception: update the leader era and try again
      } catch (LeaderEraException e) {
        synchronized(paxosRWLock) {
          certifyLeaderEraLP(e.currentEra);
        }
        
      // remote exception: try again
      } catch (RemoteException e) {
      }
    }
  }

  // Server as Acceptor
  // ------------------

  @Override
  public Set<PaxosState> paxosStatesAfterId(int paxosId, LeaderEra leaderEra)
      throws LeaderEraException, RemoteException {
    synchronized(paxosRWLock) {
      // certify our era is up-to-date; throw exception if request is obsolete
      if (!certifyLeaderEraLP(leaderEra)) {
        throw new LeaderEraException(leaderEra);
      }
      
      // collect the ids of missing ledger values known by the leader
      Set<Integer> resultsToLearn = new HashSet<Integer>();
      for (int unknownId = ledgerTopId + 1; unknownId <= paxosId; unknownId++) {
        resultsToLearn.add(unknownId);
      }
      
      // Iterate through live ballots. Ballots with ids greater than paxosId
      // should be reported. Also, avoid sending requests to learn known
      // results.
      Set<PaxosState> statesAfterId = new HashSet<PaxosState>();
      for (int id : liveBallots.keySet()) {
        PaxosState ballot = liveBallots.get(id);
        
        // collect ballots with ids above paxosId
        if (id > paxosId) {
          statesAfterId.add(ballot);
          
        // no need to learn resolved ballots
        } else if (ballot.hasSucceeded) {
          resultsToLearn.remove(id);
        }
      }
      
      // TODO - request resultsToLearn, perhaps in a separate thread

      return statesAfterId;
    }
  }

  @Override
  public boolean acceptBallot(PaxosState ballot)
      throws LeaderEraException, RemoteException {
    synchronized(paxosRWLock) {
      // certify our era is up-to-date; throw exception if request is obsolete
      if (!certifyLeaderEraLP(leaderEra)) {
        throw new LeaderEraException(leaderEra);
      }

      // store the ballot and return true for valid requests
      liveBallots.put(ballot.paxosId, ballot);
      return true;
    }
  }

  @Override
  public void recordSuccess(PaxosState ballot) throws RemoteException {
    boolean ledgerUpdateReady = false;

    synchronized(paxosRWLock) {
      // update leader era if we're behind
      certifyLeaderEraLP(ballot.leaderEra);

      // get id and check the stored ballot for success
      int id = ballot.paxosId;
      PaxosState storedBallot = liveBallots.get(id);
      boolean alreadySucceeded = storedBallot != null &&
          storedBallot.hasSucceeded;
      
      // if success is not yet known, record success
      if (ledgerTopId < id && !alreadySucceeded) {
        liveBallots.put(id, ballot);
        
        // if this success lets the ledger progress, update it
        if (id == ledgerTopId + 1) {
          ledgerUpdateReady = true;
        }
      }
    }
    
    // update the ledger. down the road it might be better to handle ledger
    // updates in a background job instead of handling them manually
    if (ledgerUpdateReady) {
      updateLedger();
    }
  }

  @Override
  public void ping(LeaderEra leaderEra)
      throws LeaderEraException, RemoteException {
    // TODO
    return;
  }


  // Server as Leader
  // ----------------

  @Override
  public boolean requestValue(PaxosValue value)
      throws LeaderEraException, RemoteException {
    // the value request and its associated condition
    Condition cond = paxosRWLock.newCondition();
    PaxosValueRequest request = new PaxosValueRequest(value, cond);

    synchronized(paxosRWLock) {
      // return if this request has already been fulfilled
      if (fulfilledRequestIds.contains(value.requestId)) {
        return true;
      }

      // if this request is already registered, listen on the existing request.
      // assert that the registered request's value matches the new one.
      if (valueRequests.containsKey(value.requestId)) {
        request = valueRequests.get(value.requestId);
        request.requesterCount++;
        cond = request.success;
        assert(request.value.equals(value));

      // else, register the new request
      } else {
        valueRequests.put(value.requestId, request);
      }

      // wait for the request to succeed
      while(!request.succeeded) {
        // quit if not the leader
        if (!isLeaderLP()) {
          // decrement requester count. remove request if no requesters remain
          request.requesterCount--;
          if (request.requesterCount == 0) {
            valueRequests.remove(value.requestId);
          }
          
          // throw leader era exception
          throw new LeaderEraException(leaderEra);
        }

        // wait to be signaled
        cond.awaitUninterruptibly();
      }
      
      // success:

      // decrement requester count. remove request if no requesters remain
      request.requesterCount--;
      if (request.requesterCount == 0) {
        valueRequests.remove(value.requestId);
      }
      
      // return success
      return true;
    }
  }
  
  // TODO
  private void beginLeadership() {
    // setup local state, run phase 1
    
    // then:
    runElections();
  }
  
  // TODO
  private void runElections() {
  }
  
  
  // Leadership Management
  // ---------------------
  
  /**
   * Whether a leader election is currently occurring.
   * 
   * @return {@code true} if a leader election is occurring, else {@code false}
   */
  private final boolean inLeaderElectionLP() {
    return leaderEra.inElection();
  }
  
  /**
   * Whether this machine is currently the leader.
   * 
   * @return {@code true} if this machine is the leader, else {@code false}
   */
  private final boolean isLeaderLP() {
    return leaderEra.leaderId.equals(uid);
  }
  
  /**
   * The current leader, if one exists, else {@code null}.
   * 
   * @return the current leader, if one exists, else {@code null}
   */
  private final PaxosDb currentLeaderLP() {
    return legislators.get(leaderEra.leaderId);
  }
  
  /**
   * Ensure that this machine is in the specified era if possible,
   * fast-forwarding from an older era if necessary. If the specified era is in
   * the past, it cannot be certified and {@code false} is returned.
   * 
   * @param  targetEra the leadership era to certify
   * @return           {@code false} if the era could not be certified, else
   *                   {@code true}
   */
  private boolean certifyLeaderEraLP(LeaderEra targetEra) {
    // compare the target era to the current era
    int cmp = targetEra.compareTo(this.leaderEra);
    
    // if the target era has been passed, it cannot be certified
    if (cmp < 0) {
      return false;

    // if the target era is ahead of the present one, fast-forward to it
    } else if (cmp > 0) {
      // as appropriate, either start a new election or conclude a leader
      if (targetEra.inElection()) {
        beginLeaderElectionLP(targetEra);
      } else {
        declareLeaderLP(targetEra);
      }
    }
    
    // we are now in the desired era
    return true;
  }

  @Override
  public void beginLeaderElection(LeaderEra leaderEra) throws RemoteException {
    // acquire lock and begin election
    synchronized(paxosRWLock) {
      beginLeaderElectionLP(leaderEra);
    }
  }

  /**
   * Handles a new leader election. Must be called with {@code paxosRWLock}
   * held. See {@link #beginLeaderElection}.
   * 
   * @param leaderEra the era of the new leader election
   * @see #declareLeaderLP
   */
  private void beginLeaderElectionLP(LeaderEra leaderEra) {
    // ignore an old message
    if (leaderEra.compareTo(this.leaderEra) <= 0) {
      return;
    }
    
    // short-circuit if this machine is already in election mode
    if (inLeaderElectionLP()) {
      this.leaderEra = leaderEra;
      return;
    }
    
    // deal with leadership era tear-down
    if (isLeaderLP()) {
      // TODO
    } else {
      // TODO
    }

    // update leader state
    this.leaderEra = leaderEra;
  }

  @Override
  public void declareLeader(LeaderEra leaderEra) throws RemoteException {
    // acquire lock and declare leader
    synchronized(paxosRWLock) {
      declareLeaderLP(leaderEra);
    }
  }

  /**
   * Resolves a leader election. Must be called with {@code paxosRWLock} held.
   * See {@link #declareLeader}.
   * 
   * @param leaderEra the era resulting from a leader election
   * @see #beginLeaderElectionLP
   */
  public void declareLeaderLP(LeaderEra leaderEra) {
    // ignore an old message
    if (leaderEra.compareTo(this.leaderEra) < 0) {
      return;
    }

    // ensure we've accounted for having begun an election
    if (!inLeaderElectionLP()) {
      beginLeaderElectionLP(leaderEra);
    }

    // update leader state
    this.leaderEra = leaderEra;

    // deal with leadership era set-up
    if (isLeaderLP()) {
      // TODO
    } else {
      // TODO
    }
    
    // signal a new leadership era
    leaderElected.signalAll();
  }
  
  @Override
  public LeaderEra leadershipState() throws RemoteException {
    synchronized(paxosRWLock) {
      return leaderEra;
    }
  }


  // Community Management
  // --------------------

  /**
   * XXX: temporary
   */
  public void addLegislator(String legislatorUid) throws RemoteException {
    try {
      // lookup legislator in registry and add it to the legislators list
      PaxosDb legislator = (PaxosDb) Naming.lookup("rmi://localhost/" +
          legislatorUid);
      legislators.put(legislatorUid, legislator);

    } catch (Exception e) {
      System.err.println("PaxosDbImpl#addLegislator exception:");
      e.printStackTrace();
    }
  }


  // Setup
  // -----

  /**
   * Create a PaxosDbImpl and register it on the RMI registry.
   */  
  public static void main(String[] args) {
    LinkedHashMap<Integer, String> v = new LinkedHashMap<Integer, String>();
    v.put(1, "A");
    v.put(1, "A");
    v.put(2, "B");
    v.put(4, "D");
    v.put(3, "C");
    
    System.out.println(v.size());
    System.out.println(v.size());
    
    return;
    /*
    // set VM arguments
    // NOTE: this relies on a symlink from /links/paxos to this project's
    // directory
    String dirPath = "file:/links/paxos/";
    System.setProperty("java.security.policy", dirPath + "security.policy");
    System.setProperty("java.rmi.server.codebase", dirPath + "bin");

    if (System.getSecurityManager() == null) {
      System.setSecurityManager(new RMISecurityManager());
    }

    try {
      PaxosDbImpl replica = new PaxosDbImpl();	

      Naming.rebind("rmi://localhost/" + replica.uid, replica);

      System.out.println("[System] " + replica.uid + " is registered");

    } catch (Exception e) {
      System.err.println("PaxosDbImpl exception:");
      e.printStackTrace();
    }
    */
  }
}
