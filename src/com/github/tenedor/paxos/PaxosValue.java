package com.github.tenedor.paxos;

public class PaxosValue {
  public String key;
  public String value;
  public int requestId;

  @Override
  public String toString() {
    return "PaxosValue [key=" + key + ", value=" + value + ", requestId=" +
        requestId + "]";
  }
}
