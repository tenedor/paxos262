package com.github.tenedor.paxos;

public class LeaderEra {
  /**
   * The leadership era. This value increases each time a leader election
   * begins.
   */
  public int era;

  /**
   * The id of the leader. If a leader election is in progress, {@code leaderId}
   * is the empty string.
   */  
  public String leaderId;

  public LeaderEra(int era, String leaderId) {
    this.era = era;
    this.leaderId = leaderId;
  }
  
  public boolean inElection() {
    return leaderId.isEmpty();
  }
  
  /**
   * Compare this leader era to another.
   * 
   * @param otherEra the leader era to be compared
   * @return         a negative integer, zero, or a positive integer as this era
   *                 precedes, equals, or succeeds {@code otherEra}
   * @throws NullPointerException
   */
  public int compareTo(LeaderEra otherEra) throws NullPointerException {
    // comparison to null is an error
    if (otherEra == null) {
      throw new NullPointerException();
    }

    // compare the era numbers
    int cmpEra = era < otherEra.era ? -1 : era > otherEra.era ? +1 : 0;
        
    // compare the election statuses
    int cmpId = leaderId.isEmpty() ? (otherEra.leaderId.isEmpty() ? 0 : -1)
        : otherEra.leaderId.isEmpty() ? +1 : 0;
    
    // judge equality first by era, then by election status
    return cmpEra == 0 ? cmpId : cmpEra;
  }
}