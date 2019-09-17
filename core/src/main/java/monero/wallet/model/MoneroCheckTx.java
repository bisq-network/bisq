package monero.wallet.model;

import java.math.BigInteger;

/**
 * Results from checking a transaction key.
 */
public class MoneroCheckTx extends MoneroCheck {

  public Boolean inTxPool;
  public Long numConfirmations;
  public BigInteger receivedAmount;
  
  public Boolean getInTxPool() {
    return inTxPool;
  }
  
  public void setInTxPool(Boolean inTxPool) {
    this.inTxPool = inTxPool;
  }
  
  public Long getNumConfirmations() {
    return numConfirmations;
  }
  
  public void setNumConfirmations(Long numConfirmations) {
    this.numConfirmations = numConfirmations;
  }
  
  public BigInteger getReceivedAmount() {
    return receivedAmount;
  }
  
  public void setReceivedAmount(BigInteger receivedAmount) {
    this.receivedAmount = receivedAmount;
  }
}
