package monero.wallet.model;

/**
 * Result from syncing a Monero wallet.
 */
public class MoneroSyncResult {

  private Long numBlocksFetched;
  private Boolean receivedMoney;
  
  public MoneroSyncResult() {
    this(null, null);
  }
  
  public MoneroSyncResult(Long numBlocksFetched, Boolean receivedMoney) {
    this.numBlocksFetched = numBlocksFetched;
    this.receivedMoney = receivedMoney;
  }
  
  public Long getNumBlocksFetched() {
    return numBlocksFetched;
  }
  
  public void setNumBlocksFetched(Long numBlocksFetched) {
    this.numBlocksFetched = numBlocksFetched;
  }
  
  public Boolean getReceivedMoney() {
    return receivedMoney;
  }
  
  public void setReceivedMoney(Boolean receivedMoney) {
    this.receivedMoney = receivedMoney;
  }
}
