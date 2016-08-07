package com.github.tenedor.paxos;

import java.rmi.*;
import java.util.*;

public interface PaxosDb extends Remote {

  /**
   * Return the paxos states for each existing Paxos instance following
   * {@code paxosId}. This is equivalent to a multi-Paxos "prepare" request.
   * <p>
   * If the specified {@code leaderEra} is obsolete, an exception is thrown
   * containing the current era.
   *  
   * @param  paxosId   the largest paxos id to not report
   * @param  leaderEra the leadership era when this request was made
   * @return           a set of Paxos states for existing Paxos instances
   * @throws LeaderEraException the specified {@code leaderEra} is obsolete
   * @throws RemoteException
   */  
  public Set<PaxosState> paxosStatesAfterId(int paxosId, LeaderEra leaderEra)
      throws LeaderEraException, RemoteException;

  /**
   * A request to accept the specified ballot. Returning {@code true} signals
   * acceptance.
   * <p>
   * If the ballot's {@code leaderEra} is obsolete, an exception is thrown
   * containing the current era.
   *
   * @param  ballot the ballot to accept
   * @return        a {@code true} boolean if the ballot is accepted
   * @throws LeaderEraException the ballot's {@code leaderEra} is obsolete
   * @throws RemoteException
   */
  public boolean acceptBallot(PaxosState ballot)
      throws LeaderEraException, RemoteException;

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
   * <p>
   * If the specified {@code leaderEra} is obsolete, an exception is thrown
   * containing the current era.
   *
   * @param leaderEra the leadership era when this ping was sent
   * @throws LeaderEraException the specified {@code leaderEra} is obsolete
   * @throws RemoteException
   */
  public void ping(LeaderEra leaderEra)
      throws LeaderEraException, RemoteException;

  /**
   * A request from a legislator to pass the specified value. Returning
   * {@code true} signifies success.
   * <p>
   * If this machine is not the leader or loses leadership, a
   * {@code LeaderEraException} is thrown.
   *
   * @param  value the value to pass
   * @return       {@code true} if the value has been passed
   * @throws LeaderEraException this machine is not the leader
   * @throws RemoteException
   */
  public boolean requestValue(PaxosValue value)
      throws LeaderEraException, RemoteException;

  /**
   * Begin a leader election in a new era. This instruction will be ignored
   * unless the given era is more recent than the machine's current era.
   * 
   * @param leaderEra the era of the new leader election
   * @throws RemoteException
   */
  public void beginLeaderElection(LeaderEra leaderEra) throws RemoteException;

  /**
   * Tell this machine the result of a leader election.
   * 
   * @param leaderEra the era resulting from a leader election
   * @throws RemoteException
   */
  public void declareLeader(LeaderEra leaderEra) throws RemoteException;

  /**
   * The current leadership era and leader.
   * 
   * @return the current leadership era
   * @throws RemoteException
   */
  public LeaderEra leadershipState() throws RemoteException;

}
