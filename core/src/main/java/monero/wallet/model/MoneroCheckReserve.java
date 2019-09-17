package monero.wallet.model;

import java.math.BigInteger;

/**
 * Results from checking a reserve proof.
 */
public class MoneroCheckReserve extends MoneroCheck {
  
  private BigInteger totalAmount;
  private BigInteger unconfirmedSpentAmount;
  
  public BigInteger getTotalAmount() {
    return totalAmount;
  }
  
  public void setTotalAmount(BigInteger totalAmount) {
    this.totalAmount = totalAmount;
  }
  
  public BigInteger getUnconfirmedSpentAmount() {
    return unconfirmedSpentAmount;
  }
  
  public void setUnconfirmedSpentAmount(BigInteger unconfirmedSpentAmount) {
    this.unconfirmedSpentAmount = unconfirmedSpentAmount;
  }
}
