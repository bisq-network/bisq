package monero.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Monero daemon connection.
 */
public class MoneroDaemonConnection {
  
  private MoneroDaemonPeer peer;
  private String id;
  private Long avgDownload;
  private Long avgUpload;
  private Long currentDownload;
  private Long currentUpload;
  private Long height;
  private Boolean isIncoming;
  private Long liveTime;
  private Boolean isLocalIp;
  private Boolean isLocalHost;
  private Integer numReceives;
  private Integer numSends;
  private Long receiveIdleTime;
  private Long sendIdleTime;
  private String state;
  private Integer numSupportFlags;
  
  public MoneroDaemonPeer getPeer() {
    return peer;
  }
  
  public void setPeer(MoneroDaemonPeer peer) {
    this.peer = peer;
  }
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public Long getAvgDownload() {
    return avgDownload;
  }
  
  public void setAvgDownload(Long avgDownload) {
    this.avgDownload = avgDownload;
  }
  
  public Long getAvgUpload() {
    return avgUpload;
  }
  
  public void setAvgUpload(Long avgUpload) {
    this.avgUpload = avgUpload;
  }
  
  public Long getCurrentDownload() {
    return currentDownload;
  }
  
  public void setCurrentDownload(Long currentDownload) {
    this.currentDownload = currentDownload;
  }
  
  public Long getCurrentUpload() {
    return currentUpload;
  }
  
  public void setCurrentUpload(Long currentUpload) {
    this.currentUpload = currentUpload;
  }
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return isIncoming;
  }
  
  public void setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
  }
  
  public Long getLiveTime() {
    return liveTime;
  }
  
  public void setLiveTime(Long liveTime) {
    this.liveTime = liveTime;
  }
  
  @JsonProperty("isLocalIp")
  public Boolean isLocalIp() {
    return isLocalIp;
  }
  
  public void setIsLocalIp(Boolean isLocalIp) {
    this.isLocalIp = isLocalIp;
  }
  
  public Boolean isLocalHost() {
    return isLocalHost;
  }
  
  public void setIsLocalHost(Boolean isLocalHost) {
    this.isLocalHost = isLocalHost;
  }
  
  public Integer getNumReceives() {
    return numReceives;
  }
  
  public void setNumReceives(Integer numReceives) {
    this.numReceives = numReceives;
  }
  
  public Integer getNumSends() {
    return numSends;
  }
  
  public void setNumSends(Integer numSends) {
    this.numSends = numSends;
  }
  
  public Long getReceiveIdleTime() {
    return receiveIdleTime;
  }
  
  public void setReceiveIdleTime(Long receiveIdleTime) {
    this.receiveIdleTime = receiveIdleTime;
  }
  
  public Long getSendIdleTime() {
    return sendIdleTime;
  }
  
  public void setSendIdleTime(Long sendIdleTime) {
    this.sendIdleTime = sendIdleTime;
  }
  
  public String getState() {
    return state;
  }
  
  public void setState(String state) {
    this.state = state;
  }
  
  public Integer getNumSupportFlags() {
    return numSupportFlags;
  }
  
  public void setNumSupportFlags(Integer numSupportFlags) {
    this.numSupportFlags = numSupportFlags;
  }
}
