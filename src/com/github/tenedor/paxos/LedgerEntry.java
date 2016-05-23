package com.github.tenedor.paxos;

public class LedgerEntry {
	  public PaxosValue value;
	  public int cumulativeHash;
	  
	  public LedgerEntry(PaxosValue value, int previousHash) {
	    this.value = value;
	    cumulativeHash = (value.toString() + previousHash).hashCode();
	  }
}
