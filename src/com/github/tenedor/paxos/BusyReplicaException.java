package com.github.tenedor.paxos;

/**
 * Thrown when a server replica receives a request it is too busy to accept. The
 * requester should reissue the request to a different replica or wait for a
 * less busy time.
 * 
 * @author Daniel Windham
 *
 */
public class BusyReplicaException extends Exception {

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = 1308535205389033176L;

}
