package com.github.tenedor.paxos;

import java.util.*;

public class PaxosLeaderState extends PaxosState {
  public int precedentLeaderEra;
  public PaxosValue precedentValue;
  Set<PaxosDb> acceptors;

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = -7474284782970134806L;

  public PaxosLeaderState() {
    acceptors = new HashSet<PaxosDb>();
  }
}
