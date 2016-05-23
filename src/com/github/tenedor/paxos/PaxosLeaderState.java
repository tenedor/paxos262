package com.github.tenedor.paxos;

import java.util.*;

public class PaxosLeaderState extends PaxosState {
	  public int precedentLeaderEra;
	  public PaxosValue precedentValue;
	  Set<PaxosDb> acceptors;

	  public PaxosLeaderState() {
	    acceptors = new HashSet<PaxosDb>();
	  }
}
