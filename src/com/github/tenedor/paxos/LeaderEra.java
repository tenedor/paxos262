package com.github.tenedor.paxos;

import java.io.Serializable;

public class LeaderEra implements Serializable {

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

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = 6911303728053035330L;

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

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + era;
    result = prime * result + ((leaderId == null) ? 0 : leaderId.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    LeaderEra other = (LeaderEra) obj;
    if (era != other.era)
      return false;
    if (leaderId == null) {
      if (other.leaderId != null)
        return false;
    } else if (!leaderId.equals(other.leaderId))
      return false;
    return true;
  }
}
