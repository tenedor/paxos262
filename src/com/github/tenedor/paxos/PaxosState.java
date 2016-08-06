package com.github.tenedor.paxos;

public class PaxosState {
  public int paxosId;
  public LeaderEra leaderEra;
  public PaxosValue value;
  public boolean hasSucceeded;
}
