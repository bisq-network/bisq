package monero.wallet.model;

import java.math.BigInteger;

/**
 * Models results from importing key images.
 */
public class MoneroKeyImageImportResult {
  
  private Long height;
  private BigInteger spentAmount;
  private BigInteger unspentAmount;
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  public BigInteger getSpentAmount() {
    return spentAmount;
  }
  
  public void setSpentAmount(BigInteger spentAmount) {
    this.spentAmount = spentAmount;
  }
  
  public BigInteger getUnspentAmount() {
    return unspentAmount;
  }
  
  public void setUnspentAmount(BigInteger unspentAmount) {
    this.unspentAmount = unspentAmount;
  }
}
