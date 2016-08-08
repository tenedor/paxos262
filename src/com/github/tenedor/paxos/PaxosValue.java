package com.github.tenedor.paxos;

import java.io.Serializable;

public class PaxosValue implements Serializable {
  public String key;
  public String value;
  public int requestId;

  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = -226462197189407130L;

  @Override
  public String toString() {
    return "PaxosValue [key=" + key + ", value=" + value + ", requestId=" +
        requestId + "]";
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + requestId;
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
    PaxosValue other = (PaxosValue) obj;
    if (key == null) {
      if (other.key != null)
        return false;
    } else if (!key.equals(other.key))
      return false;
    if (requestId != other.requestId)
      return false;
    if (value == null) {
      if (other.value != null)
        return false;
    } else if (!value.equals(other.value))
      return false;
    return true;
  }
}
