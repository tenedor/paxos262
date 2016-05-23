package com.github.tenedor.paxos;

public class PaxosState {
  public int paxosId;
  public int leaderEra;
  public PaxosValue value;
  public boolean hasSucceeded;
}
