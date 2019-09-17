package monero.wallet.model;

/**
 * Monero integrated address model.
 */
public class MoneroIntegratedAddress {

  private String standardAddress;
  private String paymentId;
  private String integratedAddress;
  
  public MoneroIntegratedAddress() {
    // necessary for deserialization
  }
  
  public MoneroIntegratedAddress(String standardAddress, String paymentId, String integratedAddress) {
    super();
    this.standardAddress = standardAddress;
    this.paymentId = paymentId;
    this.integratedAddress = integratedAddress;
  }

  public String getStandardAddress() {
    return standardAddress;
  }
  
  public void setStandardAddress(String standardAddress) {
    this.standardAddress = standardAddress;
  }
  
  public String getPaymentId() {
    return paymentId;
  }
  
  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }
  
  public String getIntegratedAddress() {
    return integratedAddress;
  }
  
  public void setIntegratedAddress(String integratedAddress) {
    this.integratedAddress = integratedAddress;
  }
  
  public String toString() {
    return integratedAddress;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((standardAddress == null) ? 0 : standardAddress.hashCode());
    result = prime * result + ((integratedAddress == null) ? 0 : integratedAddress.hashCode());
    result = prime * result + ((paymentId == null) ? 0 : paymentId.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroIntegratedAddress other = (MoneroIntegratedAddress) obj;
    if (standardAddress == null) {
      if (other.standardAddress != null) return false;
    } else if (!standardAddress.equals(other.standardAddress)) return false;
    if (integratedAddress == null) {
      if (other.integratedAddress != null) return false;
    } else if (!integratedAddress.equals(other.integratedAddress)) return false;
    if (paymentId == null) {
      if (other.paymentId != null) return false;
    } else if (!paymentId.equals(other.paymentId)) return false;
    return true;
  }
}
