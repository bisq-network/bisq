package monero.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Models a peer to the daemon.
 */
public class MoneroDaemonPeer {

  private String id;
  private String address;
  private String host;
  private Integer port;
  private Integer rpcPort;
  private Boolean isOnline;
  private Long lastSeenTimestamp;
  private Integer pruningSeed;
  
  public String getId() {
    return id;
  }
  
  public void setId(String id) {
    this.id = id;
  }
  
  public String getAddress() {
    return address;
  }
  
  public void setAddress(String address) {
    this.address = address;
  }
  
  public String getHost() {
    return host;
  }
  
  public void setHost(String host) {
    this.host = host;
  }
  
  public Integer getPort() {
    return port;
  }
  
  public void setPort(Integer port) {
    this.port = port;
  }
  
  public Integer getRpcPort() {
    return rpcPort;
  }
  
  public void setRpcPort(Integer rpcPort) {
    this.rpcPort = rpcPort;
  }
  
  @JsonProperty("isOnline")
  public Boolean isOnline() {
    return isOnline;
  }
  
  public void setIsOnline(Boolean isOnline) {
    this.isOnline = isOnline;
  }
  
  public Long getLastSeenTimestamp() {
    return lastSeenTimestamp;
  }
  
  public void setLastSeenTimestamp(Long lastSeenTimestamp) {
    this.lastSeenTimestamp = lastSeenTimestamp;
  }
  
  public Integer getPruningSeed() {
    return pruningSeed;
  }
  
  public void setPruningSeed(Integer pruningSeed) {
    this.pruningSeed = pruningSeed;
  }
}
