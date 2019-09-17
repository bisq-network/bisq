package monero.daemon.model;

import monero.utils.MoneroException;

/**
 * Enumerate key image spent statuses.
 */
public enum MoneroKeyImageSpentStatus {
  NOT_SPENT,
  CONFIRMED,
  TX_POOL;
  
  public static MoneroKeyImageSpentStatus valueOf(int status) {
    if (status == 0) return NOT_SPENT;
    else if (status == 1) return CONFIRMED;
    else if (status == 2) return TX_POOL;
    throw new MoneroException("Invalid integer value for spent status: " + status);
  }
}
