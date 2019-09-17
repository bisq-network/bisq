package monero.wallet.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonProperty;

import monero.utils.MoneroUtils;

/**
 * Monero subaddress model.
 */
public class MoneroSubaddress {

  private Integer accountIndex;
  private Integer index;
  private String address;
  private String label;
  private BigInteger balance;
  private BigInteger unlockedBalance;
  private Long numUnspentOutputs;
  private Boolean isUsed;
  private Long numBlocksToUnlock;
  
  public MoneroSubaddress() {
    // nothing to construct
  }
  
  public MoneroSubaddress(String address) {
    this.address = address;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }
  
  public MoneroSubaddress setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  public Integer getIndex() {
    return index;
  }
  
  public MoneroSubaddress setIndex(Integer index) {
    this.index = index;
    return this;
  }
  
  public String getAddress() {
    return address;
  }
  
  public MoneroSubaddress setAddress(String address) {
    this.address = address;
    return this;
  }
  
  public String getLabel() {
    return label;
  }
  
  public MoneroSubaddress setLabel(String label) {
    this.label = label;
    return this;
  }
  
  public BigInteger getBalance() {
    return balance;
  }
  
  public MoneroSubaddress setBalance(BigInteger balance) {
    this.balance = balance;
    return this;
  }
  
  public BigInteger getUnlockedBalance() {
    return unlockedBalance;
  }
  
  public MoneroSubaddress setUnlockedBalance(BigInteger unlockedBalance) {
    this.unlockedBalance = unlockedBalance;
    return this;
  }
  
  public Long getNumUnspentOutputs() {
    return numUnspentOutputs;
  }
  
  public MoneroSubaddress setNumUnspentOutputs(Long numUnspentOutputs) {
    this.numUnspentOutputs = numUnspentOutputs;
    return this;
  }
  
  @JsonProperty("isUsed")
  public Boolean isUsed() {
    return isUsed;
  }
  
  public MoneroSubaddress setIsUsed(Boolean isUsed) {
    this.isUsed = isUsed;
    return this;
  }

  public Long getNumBlocksToUnlock() {
    return numBlocksToUnlock;
  }

  public MoneroSubaddress setNumBlocksToUnlock(Long numBlocksToUnlock) {
    this.numBlocksToUnlock = numBlocksToUnlock;
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(MoneroUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(MoneroUtils.kvLine("Subaddress index", this.getIndex(), indent));
    sb.append(MoneroUtils.kvLine("Address", this.getAddress(), indent));
    sb.append(MoneroUtils.kvLine("Label", this.getLabel(), indent));
    sb.append(MoneroUtils.kvLine("Balance", this.getBalance(), indent));
    sb.append(MoneroUtils.kvLine("Unlocked balance", this.getUnlockedBalance(), indent));
    sb.append(MoneroUtils.kvLine("Num unspent outputs", this.getNumUnspentOutputs(), indent));
    sb.append(MoneroUtils.kvLine("Is used", this.isUsed(), indent));
    sb.append(MoneroUtils.kvLine("Num blocks to unlock", this.getNumBlocksToUnlock(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((address == null) ? 0 : address.hashCode());
    result = prime * result + ((balance == null) ? 0 : balance.hashCode());
    result = prime * result + ((index == null) ? 0 : index.hashCode());
    result = prime * result + ((isUsed == null) ? 0 : isUsed.hashCode());
    result = prime * result + ((label == null) ? 0 : label.hashCode());
    result = prime * result + ((numBlocksToUnlock == null) ? 0 : numBlocksToUnlock.hashCode());
    result = prime * result + ((numUnspentOutputs == null) ? 0 : numUnspentOutputs.hashCode());
    result = prime * result + ((unlockedBalance == null) ? 0 : unlockedBalance.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroSubaddress other = (MoneroSubaddress) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (address == null) {
      if (other.address != null) return false;
    } else if (!address.equals(other.address)) return false;
    if (balance == null) {
      if (other.balance != null) return false;
    } else if (!balance.equals(other.balance)) return false;
    if (index == null) {
      if (other.index != null) return false;
    } else if (!index.equals(other.index)) return false;
    if (isUsed == null) {
      if (other.isUsed != null) return false;
    } else if (!isUsed.equals(other.isUsed)) return false;
    if (label == null) {
      if (other.label != null) return false;
    } else if (!label.equals(other.label)) return false;
    if (numBlocksToUnlock == null) {
      if (other.numBlocksToUnlock != null) return false;
    } else if (!numBlocksToUnlock.equals(other.numBlocksToUnlock)) return false;
    if (numUnspentOutputs == null) {
      if (other.numUnspentOutputs != null) return false;
    } else if (!numUnspentOutputs.equals(other.numUnspentOutputs)) return false;
    if (unlockedBalance == null) {
      if (other.unlockedBalance != null) return false;
    } else if (!unlockedBalance.equals(other.unlockedBalance)) return false;
    return true;
  }
}
