package monero.daemon.model;

import java.math.BigInteger;

/**
 * Entry in a Monero output histogram (see get_output_histogram of Daemon RPC documentation).
 */
public class MoneroOutputHistogramEntry {
  
  private BigInteger amount;
  private Long numInstances;
  private Long numUnlockedInstances;
  private Long numRecentInstances;
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public void setAmount(BigInteger amount) {
    this.amount = amount;
  }
  
  public Long getNumInstances() {
    return numInstances;
  }
  
  public void setNumInstances(Long numInstances) {
    this.numInstances = numInstances;
  }
  
  public Long getNumUnlockedInstances() {
    return numUnlockedInstances;
  }
  
  public void setNumUnlockedInstances(Long numUnlockedInstances) {
    this.numUnlockedInstances = numUnlockedInstances;
  }
  
  public Long getNumRecentInstances() {
    return numRecentInstances;
  }
  
  public void setNumRecentInstances(Long numRecentInstances) {
    this.numRecentInstances = numRecentInstances;
  }
}