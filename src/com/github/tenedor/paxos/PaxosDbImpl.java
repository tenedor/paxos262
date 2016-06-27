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
   * The current leadership era. This value increases each time a leader
   * election begins.
   */  
  private int leaderEra = 0;

  /**
   * The id of the machine that is currently leader. If a leader election is in
   * progress, {@code leaderId} is the empty string.
   */  
  private String leaderId = "";

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
   * The set of values to pass, keyed by request id. When a request is selected
   * by a Paxos instance it is moved to the {@code #valuesBeingPassed} map.
   * Value requests are accessible both by LRU-ordered iteration and by key.
   */
  private LinkedHashMap<Integer, PaxosValueRequest> valueRequests =
      new LinkedHashMap<Integer, PaxosValueRequest>(100, 0.75f, true);

  /**
   * The set of values currently being passed by Paxos instances, keyed by
   * request id.
   */
  private Map<Integer, PaxosValueRequest> valuesBeingPassed =
      new HashMap<Integer, PaxosValueRequest>();

  /**
   * The current set of acceptors.
   */
  private Map<String, PaxosDb> acceptors = new HashMap<String, PaxosDb>();
  
  /**
   * A lock for reading or writing paxos state. Methods that must be called with
   * this lock held have the suffix "LP".
   */
  private final Lock paxosRWLock = new ReentrantLock();


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
        
        // update the database
        // TODO - use entry.value.requestId to guard against double-evaluating
        // requests
        localDb.put(entry.value.key, entry.value.value);

        // update the ledger top id
        ledgerTopId++;
      }
    }
  }


  // Server as Acceptor
  // ------------------

  @Override
  public Set<PaxosState> paxosStatesAfterId(int paxosId, int leaderEra,
      String leaderId) throws RemoteException {
    synchronized(paxosRWLock) {
      // certify our era is up-to-date and return null for obsolete requests
      if (!certifyLeaderEraLP(leaderEra, leaderId)) return null;
      
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
  public boolean acceptBallot(PaxosState ballot) throws RemoteException {
    synchronized(paxosRWLock) {
      // TODO - use certifyLeaderEraLP

      // return false for obsolete requests
      if (ballot.leaderEra < leaderEra) {
        return false;

      // else, store the ballot and return true
      } else {
        liveBallots.put(ballot.paxosId, ballot);
        return true;
      }
    }
  }

  @Override
  public void recordSuccess(PaxosState ballot) throws RemoteException {
    boolean ledgerUpdateReady = false;

    synchronized(paxosRWLock) {
      // TODO - use certifyLeaderEraLP

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
  public void ping(int leaderEra) throws RemoteException {
    // TODO
    return;
  }


  // Server as Leader
  // ----------------

  @Override
  public boolean requestValue(PaxosValue value) throws RemoteException {
    Condition cond = paxosRWLock.newCondition();
    PaxosValueRequest request = new PaxosValueRequest(value, cond);

    paxosRWLock.lock();
    try {
      valueRequests.put(value.requestId, request);
      while(!request.succeeded) {
        cond.await();
      }
      return true;
      
    } catch (InterruptedException e) {
      return false;

    } finally {
      paxosRWLock.unlock();
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
    return leaderId.isEmpty();
  }
  
  /**
   * Whether this machine is currently the leader.
   * 
   * @return {@code true} if this machine is the leader, else {@code false}
   */
  private final boolean isLeaderLP() {
    return leaderId.equals(uid);
  }
  
  /**
   * Compare a leadership era to the current era, accounting for both the era
   * number and whether an election has completed. Returns -1 if the given era
   * is earlier than the current era, +1 if it is later, and 0 if they are
   * equal.
   * 
   * @param  leaderEra the leadership era's number
   * @param  leaderId  the elected leader's id, empty if an election is in
   *                  progress
   * @return          -1 if the given era is earlier than the current era, +1 if
   *                  it is later, and 0 if they are equal
   */
  private int compareToCurrentLeaderEraLP(int leaderEra, String leaderId) {
    // compare the era numbers
    int cmpEra = leaderEra < this.leaderEra ? -1
        : leaderEra > this.leaderEra ? +1 : 0;
        
    // compare the election statuses
    int cmpId = leaderId.isEmpty() ? (this.leaderId.isEmpty() ? 0 : -1)
        : this.leaderId.isEmpty() ? +1 : 0;
    
    // judge equality first by era, then by election status
    return cmpEra == 0 ? cmpId : cmpEra;
  }
  
  /**
   * Ensure that this machine is in the specified era if possible,
   * fast-forwarding from an older era if necessary. If the specified era is in
   * the past, it cannot be certified and {@code false} is returned.
   * 
   * @param  leaderEra the leadership era's number
   * @param  leaderId  the elected leader's id, empty if an election is in
   *                  progress
   * @return          {@code false} if the era could not be certified, else
   *                  {@code true}
   */
  private boolean certifyLeaderEraLP(int leaderEra, String leaderId) {
    // compare this era to the current era
    int cmp = compareToCurrentLeaderEraLP(leaderEra, leaderId);
    
    // if this era has been passed, it cannot be certified
    if (cmp < 0) {
      return false;

    // if this era is ahead of the present one, fast-forward to it
    } else if (cmp > 0) {
      // as appropriate, either start a new election or conclude a leader
      if (leaderId.isEmpty()) {
        beginLeaderElectionLP(leaderEra);
      } else {
        declareLeaderLP(leaderEra, leaderId);
      }
    }
    
    // we are now in the desired era
    return true;
  }

  @Override
  public void beginLeaderElection(int leaderEra) throws RemoteException {
    // acquire lock and begin election
    synchronized(paxosRWLock) {
      beginLeaderElectionLP(leaderEra);
    }
  }

  /**
   * Handles a new leader election. Must be called with {@code paxosRWLock}
   * held. See {@link #beginLeaderElection}.
   * 
   * @param leaderEra the new era of the leader election
   * @see #declareLeaderLP
   */
  private void beginLeaderElectionLP(int leaderEra) {
    // ignore an old message
    if (leaderEra <= this.leaderEra) {
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
    leaderId = "";
  }

  @Override
  public void declareLeader(int leaderEra, String leaderId)
      throws RemoteException {
    // acquire lock and declare leader
    synchronized(paxosRWLock) {
      declareLeaderLP(leaderEra, leaderId);
    }
  }

  /**
   * Resolves a leader election. Must be called with {@code paxosRWLock} held.
   * See {@link #declareLeader}.
   * 
   * @param leaderEra the election's era number
   * @param leaderId the elected leader's id
   * @see #beginLeaderElectionLP
   */
  public void declareLeaderLP(int leaderEra, String leaderId) {
    // ignore an old message
    if (leaderEra < this.leaderEra) {
      return;
    }

    // ensure we've accounted for having begun an election
    if (!inLeaderElectionLP()) {
      beginLeaderElectionLP(leaderEra);
    }

    // update leader state
    this.leaderEra = leaderEra;
    this.leaderId = leaderId;

    // deal with leadership era set-up
    if (isLeaderLP()) {
      // TODO
    } else {
      // TODO
    }
  }
  
  @Override
  public Pair<Integer, String> leadershipState() throws RemoteException {
    synchronized(paxosRWLock) {
      return Pair.of(leaderEra, leaderId);
    }
  }


  // Community Management
  // --------------------

  /**
   * XXX: temporary
   */
  public void addAcceptor(String acceptorUid) throws RemoteException {
    try {
      // lookup acceptor in registry and add it to the acceptors list
      PaxosDb acceptor = (PaxosDb) Naming.lookup("rmi://localhost/" +
          acceptorUid);
      acceptors.put(acceptorUid, acceptor);

    } catch (Exception e) {
      System.err.println("PaxosDbImpl#addAcceptor exception:");
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
