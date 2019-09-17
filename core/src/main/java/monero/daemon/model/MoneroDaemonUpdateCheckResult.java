package monero.daemon.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Models the result of checking for a daemon update.
 */
public class MoneroDaemonUpdateCheckResult {

  private Boolean isUpdateAvailable;
  private String version;
  private String hash;
  private String autoUri;
  private String userUri;
  
  public MoneroDaemonUpdateCheckResult() {
    // nothing to construct
  }
  
  MoneroDaemonUpdateCheckResult(MoneroDaemonUpdateCheckResult checkResult) {
    this.isUpdateAvailable = checkResult.isUpdateAvailable;
    this.version = checkResult.version;
    this.hash = checkResult.hash;
    this.autoUri = checkResult.autoUri;
    this.userUri = checkResult.userUri;
  }
  
  @JsonProperty("isUpdateAvailable")
  public Boolean isUpdateAvailable() {
    return isUpdateAvailable;
  }
  
  public void setIsUpdateAvailable(Boolean isUpdateAvailable) {
    this.isUpdateAvailable = isUpdateAvailable;
  }
  
  public String getVersion() {
    return version;
  }
  
  public void setVersion(String version) {
    this.version = version;
  }
  
  public String getHash() {
    return hash;
  }
  
  public void setHash(String hash) {
    this.hash = hash;
  }
  
  public String getAutoUri() {
    return autoUri;
  }
  
  public void setAutoUri(String autoUri) {
    this.autoUri = autoUri;
  }
  
  public String getUserUri() {
    return userUri;
  }
  
  public void setUserUri(String userUri) {
    this.userUri = userUri;
  }
}
