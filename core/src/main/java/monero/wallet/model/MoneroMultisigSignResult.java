package monero.wallet.model;

import java.util.List;

/**
 * Models the result of signing multisig tx hex.
 */
public class MoneroMultisigSignResult {
  
  private String signedMultisigTxHex;
  private List<String> txIds;

  public String getSignedMultisigTxHex() {
    return signedMultisigTxHex;
  }

  public void setSignedMultisigTxHex(String signedTxMultisigHex) {
    this.signedMultisigTxHex = signedTxMultisigHex;
  }

  public List<String> getTxIds() {
    return txIds;
  }

  public void setTxIds(List<String> txIds) {
    this.txIds = txIds;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((signedMultisigTxHex == null) ? 0 : signedMultisigTxHex.hashCode());
    result = prime * result + ((txIds == null) ? 0 : txIds.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroMultisigSignResult other = (MoneroMultisigSignResult) obj;
    if (signedMultisigTxHex == null) {
      if (other.signedMultisigTxHex != null) return false;
    } else if (!signedMultisigTxHex.equals(other.signedMultisigTxHex)) return false;
    if (txIds == null) {
      if (other.txIds != null) return false;
    } else if (!txIds.equals(other.txIds)) return false;
    return true;
  }
}
