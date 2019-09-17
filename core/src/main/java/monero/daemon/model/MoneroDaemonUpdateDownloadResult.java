package monero.daemon.model;

/**
 * Models the result of downloading an update.
 */
public class MoneroDaemonUpdateDownloadResult extends MoneroDaemonUpdateCheckResult {

  private String downloadPath;
  
  public MoneroDaemonUpdateDownloadResult(MoneroDaemonUpdateCheckResult checkResult) {
    super(checkResult);
  }

  public String getDownloadPath() {
    return downloadPath;
  }

  public void setDownloadPath(String downloadPath) {
    this.downloadPath = downloadPath;
  }
}
