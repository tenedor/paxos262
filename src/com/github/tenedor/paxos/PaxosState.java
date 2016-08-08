package com.github.tenedor.paxos;

import java.io.Serializable;

public class PaxosState implements Serializable {
  public int paxosId;
  public LeaderEra leaderEra;
  public PaxosValue value;
  public boolean hasSucceeded;

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = 5456451235349678513L;

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (hasSucceeded ? 1231 : 1237);
    result = prime * result + ((leaderEra == null) ? 0 : leaderEra.hashCode());
    result = prime * result + paxosId;
    result = prime * result + ((value == null) ? 0 : value.hashCode());
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
    PaxosState other = (PaxosState) obj;
    if (hasSucceeded != other.hasSucceeded)
      return false;
    if (leaderEra == null) {
      if (other.leaderEra != null)
        return false;
    } else if (!leaderEra.equals(other.leaderEra))
      return false;
    if (paxosId != other.paxosId)
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }
}
