package monero.wallet.model;

import static org.junit.Assert.assertNotNull;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import common.utils.GenUtils;
import monero.daemon.model.MoneroTx;
import monero.utils.MoneroUtils;

/**
 * Groups transactions who share common hex data which is needed in order to
 * sign and submit the transactions.
 * 
 * For example, multisig transactions created from sendSplit() share a common
 * hex string which is needed in order to sign and submit the multisig
 * transactions.
 */
public class MoneroTxSet {

  private List<MoneroTxWallet> txs;
  private String multisigTxHex;
  private String unsignedTxHex;
  private String signedTxHex;
  
  @JsonManagedReference("tx_set")
  public List<MoneroTxWallet> getTxs() {
    return txs;
  }

  @JsonProperty("txs")
  public MoneroTxSet setTxs(List<MoneroTxWallet> txs) {
    this.txs = txs;
    return this;
  }
  
  @JsonIgnore
  public MoneroTxSet setTxs(MoneroTxWallet... txs) {
    this.txs = GenUtils.arrayToList(txs);
    return this;
  }

  public String getMultisigTxHex() {
    return multisigTxHex;
  }
  
  public MoneroTxSet setMultisigTxHex(String multisigTxHex) {
    this.multisigTxHex = multisigTxHex;
    return this;
  }
  
  public String getUnsignedTxHex() {
    return unsignedTxHex;
  }
  
  public MoneroTxSet setUnsignedTxHex(String unsignedTxHex) {
    this.unsignedTxHex = unsignedTxHex;
    return this;
  }
  
  public String getSignedTxHex() {
    return signedTxHex;
  }
  
  public MoneroTxSet setSignedTxHex(String signedTxHex) {
    this.signedTxHex = signedTxHex;
    return this;
  }
  
  public MoneroTxSet merge(MoneroTxSet txSet) {
    assertNotNull(txSet);
    if (this == txSet) return this;
    
    // merge sets
    this.setMultisigTxHex(MoneroUtils.reconcile(this.getMultisigTxHex(), txSet.getMultisigTxHex()));
    this.setUnsignedTxHex(MoneroUtils.reconcile(this.getUnsignedTxHex(), txSet.getUnsignedTxHex()));
    this.setSignedTxHex(MoneroUtils.reconcile(this.getSignedTxHex(), txSet.getSignedTxHex()));
    
    // merge txs
    if (txSet.getTxs() != null) {
      for (MoneroTxWallet tx : txSet.getTxs()) {
        tx.setTxSet(this);
        MoneroUtils.mergeTx(txs, tx);
      }
    }

    return this;
  }
  
  @Override
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(MoneroUtils.kvLine("Multisig tx hex: ", getMultisigTxHex(), indent));
    sb.append(MoneroUtils.kvLine("Unsigned tx hex: ", getUnsignedTxHex(), indent));
    sb.append(MoneroUtils.kvLine("Signed tx hex: ", getSignedTxHex(), indent));
    if (getTxs() != null) {
      sb.append(MoneroUtils.kvLine("Txs", "", indent));
      for (MoneroTx tx : getTxs()) {
        sb.append(tx.toString(indent + 1) + "\n");
      }
    }
    return sb.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((multisigTxHex == null) ? 0 : multisigTxHex.hashCode());
    result = prime * result + ((signedTxHex == null) ? 0 : signedTxHex.hashCode());
    result = prime * result + ((unsignedTxHex == null) ? 0 : unsignedTxHex.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroTxSet other = (MoneroTxSet) obj;
    if (multisigTxHex == null) {
      if (other.multisigTxHex != null) return false;
    } else if (!multisigTxHex.equals(other.multisigTxHex)) return false;
    if (signedTxHex == null) {
      if (other.signedTxHex != null) return false;
    } else if (!signedTxHex.equals(other.signedTxHex)) return false;
    if (unsignedTxHex == null) {
      if (other.unsignedTxHex != null) return false;
    } else if (!unsignedTxHex.equals(other.unsignedTxHex)) return false;
    return true;
  }
}
