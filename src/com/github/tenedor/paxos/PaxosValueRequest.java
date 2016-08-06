package com.github.tenedor.paxos;

import java.util.concurrent.locks.*;

public class PaxosValueRequest {
  public PaxosValue value;
  public boolean succeeded;
  public Condition success;
  public int requesterCount = 1;

  public PaxosValueRequest(PaxosValue value, Condition success) {
    this.value = value;
    this.succeeded = false;
    this.success = success;
  }
}
