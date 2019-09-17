package monero.daemon.model;

/**
 * Monero daemon connection span.
 */
public class MoneroDaemonConnectionSpan {

  private String connectionId;
  private Long numBlocks;
  private String remoteAddress;
  private Long rate;
  private Long speed;
  private Long size;
  private Long startBlockHeight;
  
  public String getConnectionId() {
    return connectionId;
  }
  
  public void setConnectionId(String connectionId) {
    this.connectionId = connectionId;
  }
  
  public Long getNumBlocks() {
    return numBlocks;
  }
  
  public void setNumBlocks(Long numBlocks) {
    this.numBlocks = numBlocks;
  }
  
  public String getRemoteAddress() {
    return remoteAddress;
  }
  
  public void setRemoteAddress(String remoteAddress) {
    this.remoteAddress = remoteAddress;
  }
  
  public Long getRate() {
    return rate;
  }
  
  public void setRate(Long rate) {
    this.rate = rate;
  }
  
  public Long getSpeed() {
    return speed;
  }
  
  public void setSpeed(Long speed) {
    this.speed = speed;
  }
  
  public Long getSize() {
    return size;
  }
  
  public void setSize(Long size) {
    this.size = size;
  }
  
  public Long getStartHeight() {
    return startBlockHeight;
  }
  
  public void setStartHeight(Long startHeight) {
    this.startBlockHeight = startHeight;
  }
}
