package com.github.tenedor.paxos;

/**
 * Thrown when a specified leader era is out-of-date. The exception's
 * {@link #currentEra} field contains the era at the time of the exception's
 * construction.
 * 
 * @author Daniel Windham
 *
 */
public class LeaderEraException extends Exception {

  /**
   * the leader era when this exception was constructed
   */
  public LeaderEra currentEra;
  
  /**
   * version uid for serializability
   */
  private static final long serialVersionUID = 1646036347060644502L;

  public LeaderEraException(LeaderEra currentEra) {
    super();
    this.currentEra = currentEra;
  }

  public LeaderEraException(LeaderEra currentEra, String message) {
    super(message);
    this.currentEra = currentEra;
  }

  public LeaderEraException(LeaderEra currentEra, Throwable cause) {
    super(cause);
    this.currentEra = currentEra;
  }

  public LeaderEraException(LeaderEra currentEra, String message,
      Throwable cause) {
    super(message, cause);
    this.currentEra = currentEra;
  }

  public LeaderEraException(LeaderEra currentEra, String message,
      Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
    super(message, cause, enableSuppression, writableStackTrace);
    this.currentEra = currentEra;
  }

}
