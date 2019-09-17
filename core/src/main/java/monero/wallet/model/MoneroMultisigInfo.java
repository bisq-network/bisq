package monero.wallet.model;

/**
 * Models information about a multisig wallet.
 */
public class MoneroMultisigInfo {

  private boolean isMultisig;
  private Boolean isReady;
  private Integer threshold;
  private Integer numParticipants;
  
  public boolean isMultisig() {
    return isMultisig;
  }
  
  public void setIsMultisig(boolean isMultisig) {
    this.isMultisig = isMultisig;
  }
  
  public Boolean isReady() {
    return isReady;
  }
  
  public void setIsReady(Boolean isReady) {
    this.isReady = isReady;
  }
  
  public Integer getThreshold() {
    return threshold;
  }
  
  public void setThreshold(Integer threshold) {
    this.threshold = threshold;
  }
  
  public Integer getNumParticipants() {
    return numParticipants;
  }
  
  public void setNumParticipants(Integer numParticipants) {
    this.numParticipants = numParticipants;
  }
}
