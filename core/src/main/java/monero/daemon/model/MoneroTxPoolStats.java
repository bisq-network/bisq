package monero.daemon.model;

/**
 * Models transaction pool statistics.
 */
public class MoneroTxPoolStats {

  private Integer numTxs;
  private Integer numNotRelayed;
  private Integer numFailing;
  private Integer numDoubleSpends;
  private Integer num10m;
  private Long feeTotal;
  private Long bytesMax;
  private Long bytesMed;
  private Long bytesMin;
  private Long bytesTotal;
  private Object histo;
  private Long histo98pc;
  private Long oldestTimestamp;
  
  public Integer getNumTxs() {
    return numTxs;
  }
  
  public void setNumTxs(Integer numTxs) {
    this.numTxs = numTxs;
  }
  
  public Integer getNumNotRelayed() {
    return numNotRelayed;
  }
  
  public void setNumNotRelayed(Integer numNotRelayed) {
    this.numNotRelayed = numNotRelayed;
  }
  
  public Integer getNumFailing() {
    return numFailing;
  }
  
  public void setNumFailing(Integer numFailing) {
    this.numFailing = numFailing;
  }
  
  public Integer getNumDoubleSpends() {
    return numDoubleSpends;
  }
  
  public void setNumDoubleSpends(Integer numDoubleSpends) {
    this.numDoubleSpends = numDoubleSpends;
  }
  
  public Integer getNum10m() {
    return num10m;
  }
  
  public void setNum10m(Integer num10m) {
    this.num10m = num10m;
  }
  
  public Long getFeeTotal() {
    return feeTotal;
  }
  
  public void setFeeTotal(Long feeTotal) {
    this.feeTotal = feeTotal;
  }
  
  public Long getBytesMax() {
    return bytesMax;
  }
  
  public void setBytesMax(Long bytesMax) {
    this.bytesMax = bytesMax;
  }
  
  public Long getBytesMed() {
    return bytesMed;
  }
  
  public void setBytesMed(Long bytesMed) {
    this.bytesMed = bytesMed;
  }
  
  public Long getBytesMin() {
    return bytesMin;
  }
  
  public void setBytesMin(Long bytesMin) {
    this.bytesMin = bytesMin;
  }
  
  public Long getBytesTotal() {
    return bytesTotal;
  }
  
  public void setBytesTotal(Long bytesTotal) {
    this.bytesTotal = bytesTotal;
  }
  
  public Object getHisto() {
    return histo;
  }
  
  public void setHisto(Object histo) {
    this.histo = histo;
  }
  
  public Long getHisto98pc() {
    return histo98pc;
  }
  
  public void setHisto98pc(Long histo98pc) {
    this.histo98pc = histo98pc;
  }
  
  public Long getOldestTimestamp() {
    return oldestTimestamp;
  }
  
  public void setOldestTimestamp(Long oldestTimestamp) {
    this.oldestTimestamp = oldestTimestamp;
  }
}
