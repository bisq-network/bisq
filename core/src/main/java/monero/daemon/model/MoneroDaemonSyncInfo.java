package monero.daemon.model;

import java.util.List;

/**
 * Models daemon synchronization information.
 */
public class MoneroDaemonSyncInfo {

  private Long height;
  private List<MoneroDaemonConnection> connections;
  private List<MoneroDaemonConnectionSpan> spans;
  private Long targetHeight;
  private Integer nextNeededPruningSeed;
  private String overview;
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  public List<MoneroDaemonConnection> getConnections() {
    return connections;
  }
  
  public void setConnections(List<MoneroDaemonConnection> connections) {
    this.connections = connections;
  }
  
  public List<MoneroDaemonConnectionSpan> getSpans() {
    return spans;
  }
  
  public void setSpans(List<MoneroDaemonConnectionSpan> spans) {
    this.spans = spans;
  }
  
  public Long getTargetHeight() {
    return targetHeight;
  }
  
  public void setTargetHeight(Long targetHeight) {
    this.targetHeight = targetHeight;
  }
  
  public Integer getNextNeededPruningSeed() {
    return nextNeededPruningSeed;
  }
  
  public void setNextNeededPruningSeed(Integer nextNeededPruningSeed) {
    this.nextNeededPruningSeed = nextNeededPruningSeed;
  }
  
  public String getOverview() {
    return overview;
  }
  
  public void setOverview(String overview) {
    this.overview = overview;
  }
}
