package monero.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Monero hard fork info.
 */
public class MoneroHardForkInfo {

  private Long earliestHeight;
  private Boolean isEnabled;
  private Integer state;
  private Integer threshold;
  private Integer version;
  private Integer numVotes;
  private Integer window;
  private Integer voting;
  
  public Long getEarliestHeight() {
    return earliestHeight;
  }
  
  public void setEarliestHeight(Long earliestHeight) {
    this.earliestHeight = earliestHeight;
  }
  
  @JsonProperty("isEnabled")
  public Boolean isEnabled() {
    return isEnabled;
  }
  
  public void setIsEnabled(Boolean isEnabled) {
    this.isEnabled = isEnabled;
  }
  
  public Integer getState() {
    return state;
  }
  
  public void setState(Integer state) {
    this.state = state;
  }
  
  public Integer getThreshold() {
    return threshold;
  }
  
  public void setThreshold(Integer threshold) {
    this.threshold = threshold;
  }
  
  public Integer getVersion() {
    return version;
  }
  
  public void setVersion(Integer version) {
    this.version = version;
  }
  
  public Integer getNumVotes() {
    return numVotes;
  }
  
  public void setNumVotes(Integer numVotes) {
    this.numVotes = numVotes;
  }
  
  public Integer getWindow() {
    return window;
  }
  
  public void setWindow(Integer window) {
    this.window = window;
  }
  
  public Integer getVoting() {
    return voting;
  }
  
  public void setVoting(Integer voting) {
    this.voting = voting;
  }
}
