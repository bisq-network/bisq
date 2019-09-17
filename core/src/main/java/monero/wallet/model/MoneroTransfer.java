package monero.wallet.model;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import monero.utils.MoneroUtils;

/**
 * Models a base transfer of funds to or from the wallet.
 * 
 * Transfers are either of type MoneroIncomingTransfer or MoneroOutgoingTransfer so this class is abstract.
 */
public abstract class MoneroTransfer {

  private MoneroTxWallet tx;
  private BigInteger amount;
  private Integer accountIndex;
  private Long numSuggestedConfirmations;
  
  public MoneroTransfer() {
    // nothing to initialize
  }
  
  public MoneroTransfer(final MoneroTransfer transfer) {
    this.amount = transfer.amount;
    this.accountIndex = transfer.accountIndex;
    this.numSuggestedConfirmations = transfer.numSuggestedConfirmations;
  }
  
  public abstract MoneroTransfer copy();
  
  @JsonBackReference
  public MoneroTxWallet getTx() {
    return tx;
  }
  
  public MoneroTransfer setTx(MoneroTxWallet tx) {
    this.tx = tx;
    return this;
  }
  
  @JsonProperty("isOutgoing")
  public Boolean isOutgoing() {
    return !isIncoming();
  }
  
  @JsonProperty("isIncoming")
  public abstract Boolean isIncoming();
  
  public BigInteger getAmount() {
    return amount;
  }
  
  public MoneroTransfer setAmount(BigInteger amount) {
    this.amount = amount;
    return this;
  }
  
  public Integer getAccountIndex() {
    return accountIndex;
  }

  public MoneroTransfer setAccountIndex(Integer accountIndex) {
    this.accountIndex = accountIndex;
    return this;
  }
  
  /**
   * Return how many confirmations till it's not economically worth re-writing the chain.
   * That is, the number of confirmations before the transaction is highly unlikely to be
   * double spent or overwritten and may be considered settled, e.g. for a merchant to trust
   * as finalized.
   * 
   * @return Integer is the number of confirmations before it's not worth rewriting the chain
   */
  public Long getNumSuggestedConfirmations() {
    return numSuggestedConfirmations;
  }
  
  public MoneroTransfer setNumSuggestedConfirmations(Long numSuggestedConfirmations) {
    this.numSuggestedConfirmations = numSuggestedConfirmations;
    return this;
  }
  
  /**
   * Updates this transaction by merging the latest information from the given
   * transaction.
   * 
   * Merging can modify or build references to the transfer given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param transfer is the transfer to merge into this one
   */
  public MoneroTransfer merge(MoneroTransfer transfer) {
    assert(transfer instanceof MoneroTransfer);
    if (this == transfer) return this;
    
    // merge txs if they're different which comes back to merging transfers
    if (this.getTx() != transfer.getTx()) {
      this.getTx().merge(transfer.getTx());
      return this;
    }
    
    // otherwise merge transfer fields
    this.setAccountIndex(MoneroUtils.reconcile(this.getAccountIndex(), transfer.getAccountIndex()));
    
    // TODO monero core: failed tx in pool (after testUpdateLockedDifferentAccounts()) causes non-originating saved wallets to return duplicate incoming transfers but one has amount/numSuggestedConfirmations of 0
    if (this.getAmount() != null && transfer.getAmount() != null && !this.getAmount().equals(transfer.getAmount()) && (BigInteger.valueOf(0).equals(this.getAmount()) || BigInteger.valueOf(0).equals(transfer.getAmount()))) {
      this.setAmount(MoneroUtils.reconcile(this.getAmount(), transfer.getAmount(), null, null, true));
      this.setNumSuggestedConfirmations(MoneroUtils.reconcile(this.getNumSuggestedConfirmations(), transfer.getNumSuggestedConfirmations(), null, null, true));
      System.out.println("WARNING: failed tx in pool causes non-originating wallets to return duplicate incoming transfers but with one amount/numSuggestedConfirmations of 0");
    } else {
      this.setAmount(MoneroUtils.reconcile(this.getAmount(), transfer.getAmount()));
      this.setNumSuggestedConfirmations(MoneroUtils.reconcile(this.getNumSuggestedConfirmations(), transfer.getNumSuggestedConfirmations(), null, null, false));  // TODO monero-wallet-rpc: outgoing txs become 0 when confirmed
    }
    
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(MoneroUtils.kvLine("Amount", this.getAmount() != null ? this.getAmount().toString() : null, indent));
    sb.append(MoneroUtils.kvLine("Account index", this.getAccountIndex(), indent));
    sb.append(MoneroUtils.kvLine("Num suggested confirmations", getNumSuggestedConfirmations(), indent));
    String str = sb.toString();
    return str.isEmpty() ? str :  str.substring(0, str.length() - 1);	  // strip last newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((accountIndex == null) ? 0 : accountIndex.hashCode());
    result = prime * result + ((amount == null) ? 0 : amount.hashCode());
    result = prime * result + ((numSuggestedConfirmations == null) ? 0 : numSuggestedConfirmations.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroTransfer other = (MoneroTransfer) obj;
    if (accountIndex == null) {
      if (other.accountIndex != null) return false;
    } else if (!accountIndex.equals(other.accountIndex)) return false;
    if (amount == null) {
      if (other.amount != null) return false;
    } else if (!amount.equals(other.amount)) return false;
    if (numSuggestedConfirmations == null) {
      if (other.numSuggestedConfirmations != null) return false;
    } else if (!numSuggestedConfirmations.equals(other.numSuggestedConfirmations)) return false;
    return true;
  }
}
