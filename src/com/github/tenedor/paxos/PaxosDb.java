package com.github.tenedor.paxos;

import java.rmi.*;
import java.util.*;

public interface PaxosDb extends Remote {

  /**
   * Return the paxos states for each existing Paxos instance following
   * {@code paxosId}. This is equivalent to a multi-Paxos "prepare" request.
   *  
   * @param  paxosId   the largest paxos id to not report
   * @param  leaderEra the era number of the leader making this request
   * @param  leaderId  the id of the era's elected leader
   * @return           a set of Paxos states for existing Paxos instances, or
   *                   {@code null} if the era is out of date
   * @throws RemoteException
   */  
  public Set<PaxosState> paxosStatesAfterId(int paxosId, int leaderEra,
      String leaderId) throws RemoteException;

  /**
   * A request to accept the specified ballot. Returning {@code true} signals
   * acceptance, while returning {@code false} signals that the ballot's
   * leadership era is obsolete.
   *
   * @param  ballot the ballot to accept
   * @return        a {@code true} boolean if the ballot is accepted
   * @throws RemoteException
   */
  public boolean acceptBallot(PaxosState ballot) throws RemoteException;

  /**
   * A notification that a ballot succeeded.
   *
   * @param ballot the ballot that succeeded
   * @throws RemoteException
   */
  public void recordSuccess(PaxosState ballot) throws RemoteException;

  /**
   * A notification that a Paxos leader is still active. A leader may ping an
   * acceptor that it has not interacted with recently to forestall a leader
   * election.
   *
   * @param leaderEra the era number of the leader making this request
   * @throws RemoteException
   */
  public void ping(int leaderEra) throws RemoteException;

  /**
   * A request to pass the specified value. Returning {@code true} signifies
   * success. If this machine is not the leader or loses leadership, this will
   * return {@code false}.
   *
   * @param  request the value to pass
   * @return         {@code true} if the value has been passed
   * @throws RemoteException
   */
  public boolean requestValue(PaxosValue request) throws RemoteException;

  /**
   * Begin a leader election in a new era. This instruction will be ignored
   * unless the given era is more recent than the machine's current era.
   * 
   * @param leaderEra the new era of the leader election
   * @throws RemoteException
   */
  public void beginLeaderElection(int leaderEra) throws RemoteException;

  /**
   * Tell this machine the result of a leader election.
   * 
   * @param leaderEra the election's era number
   * @param leaderId  the elected leader's id
   * @throws RemoteException
   */
  public void declareLeader(int leaderEra, String leaderId)
      throws RemoteException;

  /**
   * The current leadership era and leader.
   * 
   * @return the current leadership era number and leader id in a {@code Pair}
   * @throws RemoteException
   */
  public Pair<Integer, String> leadershipState() throws RemoteException;

}
