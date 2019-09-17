package monero.wallet.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import monero.daemon.model.MoneroOutput;
import monero.daemon.model.MoneroTx;
import monero.utils.MoneroException;
import monero.utils.MoneroUtils;

/**
 * Models a Monero output with wallet extensions.
 */
public class MoneroOutputWallet extends MoneroOutput {

  private Integer accountIndex;
  private Integer subaddressIndex;
  private Boolean isSpent;
  private Boolean isUnlocked;
  private Boolean isFrozen;
  
  public MoneroOutputWallet() {
    // nothing to construct
  }
  
  /**
   * Deep copy constructor.
   * 
   * @param output is the output to initialize from
   */
  public MoneroOutputWallet(final MoneroOutputWallet output) {
    super(output);
    this.accountIndex = output.accountIndex;
    this.subaddressIndex = output.subaddressIndex;
    this.isSpent = output.isSpent;
    this.isUnlocked = output.isUnlocked;
    this.isFrozen = output.isFrozen;
  }
  
  public MoneroOutputWallet copy() {
    return new MoneroOutputWallet(this);
  }
  
  public MoneroTxWallet getTx() {
    return (MoneroTxWallet) super.getTx();
  }
  
  @JsonIgnore
  public MoneroOutputWallet setTx(MoneroTx tx) {
    if (tx != null && !(tx instanceof MoneroTxWallet)) throw new MoneroException("Wallet output's transaction must be of type MoneroTxWallet");
    super.setTx(tx);
    return this;
  }
  
  @JsonProperty("tx")
  public MoneroOutputWallet setTx(MoneroTxWallet tx) {
    super.setTx(tx);
    return this;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }
  
  public MoneroOutputWallet setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  public Integer getSubaddressIndex() {
    return subaddressIndex;
  }
  
  public MoneroOutputWallet setSubaddressIndex(Integer subaddressIndex) {
    this.subaddressIndex = subaddressIndex;
    return this;
  }
  
  @JsonProperty("isSpent")
  public Boolean isSpent() {
    return isSpent;
  }
  
  public MoneroOutputWallet setIsSpent(Boolean isSpent) {
    this.isSpent = isSpent;
    return this;
  }
  
  @JsonProperty("isUnlocked")
  public Boolean isUnlocked() {
    return isUnlocked;
  }
  
  public MoneroOutputWallet setIsUnlocked(Boolean isUnlocked) {
    this.isUnlocked = isUnlocked;
    return this;
  }
  
  /**
   * Indicates if this output has been deemed 'malicious' and will therefore
   * not be spent by the wallet.
   * 
   * @return Boolean is whether or not this output is frozen
   */
  @JsonProperty("isFrozen")
  public Boolean isFrozen() {
    return isFrozen;
  }
  
  public MoneroOutputWallet setIsFrozen(Boolean isFrozen) {
    this.isFrozen = isFrozen;
    return this;
  }
  
  public MoneroOutputWallet merge(MoneroOutput output) {
    return merge((MoneroOutputWallet) output);
  }
  
  public MoneroOutputWallet merge(MoneroOutputWallet output) {
    if (this == output) return this;
    super.merge(output);
    this.setAccountIndex(MoneroUtils.reconcile(this.getAccountIndex(), output.getAccountIndex()));
    this.setSubaddressIndex(MoneroUtils.reconcile(this.getSubaddressIndex(), output.getSubaddressIndex()));
    this.setIsSpent(MoneroUtils.reconcile(this.isSpent(), output.isSpent(), null, true, null)); // output can become spent
    return this;
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent) + "\n");
    sb.append(MoneroUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(MoneroUtils.kvLine("Subaddress index", this.getSubaddressIndex(), indent));
    sb.append(MoneroUtils.kvLine("Is spent", this.isSpent(), indent));
    sb.append(MoneroUtils.kvLine("Is unlocked", this.isUnlocked(), indent));
    sb.append(MoneroUtils.kvLine("Is frozen", this.isFrozen(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((isFrozen == null) ? 0 : isFrozen.hashCode());
    result = prime * result + ((isSpent == null) ? 0 : isSpent.hashCode());
    result = prime * result + ((isUnlocked == null) ? 0 : isUnlocked.hashCode());
    result = prime * result + ((subaddressIndex == null) ? 0 : subaddressIndex.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroOutputWallet other = (MoneroOutputWallet) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (isFrozen == null) {
      if (other.isFrozen != null) return false;
    } else if (!isFrozen.equals(other.isFrozen)) return false;
    if (isSpent == null) {
      if (other.isSpent != null) return false;
    } else if (!isSpent.equals(other.isSpent)) return false;
    if (isUnlocked == null) {
      if (other.isUnlocked != null) return false;
    } else if (!isUnlocked.equals(other.isUnlocked)) return false;
    if (subaddressIndex == null) {
      if (other.subaddressIndex != null) return false;
    } else if (!subaddressIndex.equals(other.subaddressIndex)) return false;
    return true;
  }
}
