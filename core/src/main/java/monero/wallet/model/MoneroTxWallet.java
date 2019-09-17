package monero.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroOutput;
import monero.daemon.model.MoneroTx;
import monero.utils.MoneroException;
import monero.utils.MoneroUtils;

/**
 * Models a Monero transaction with wallet extensions.
 */
public class MoneroTxWallet extends MoneroTx {

  private MoneroTxSet txSet;
  private List<MoneroIncomingTransfer> incomingTransfers;
  private MoneroOutgoingTransfer outgoingTransfer;
  private String note;
  
  public MoneroTxWallet() {
    // nothing to initialize
  }
  
  public MoneroTxWallet(final MoneroTxWallet tx) {
    super(tx);
    this.txSet = tx.txSet;
    if (tx.incomingTransfers != null) {
      this.incomingTransfers = new ArrayList<MoneroIncomingTransfer>();
      for (MoneroIncomingTransfer transfer : tx.incomingTransfers) {
        this.incomingTransfers.add(transfer.copy().setTx(this));
      }
    }
    if (tx.outgoingTransfer != null) this.outgoingTransfer = tx.outgoingTransfer.copy().setTx(this);
    this.note = tx.note;
  }
  
  public MoneroTxWallet copy() {
    return new MoneroTxWallet(this);
  }
  
  @JsonBackReference("tx_set")
  public MoneroTxSet getTxSet() {
    return txSet;
  }
  
  public MoneroTxWallet setTxSet(MoneroTxSet txSet) {
    this.txSet = txSet;
    return this;
  }
  
  @JsonProperty("isOutgoing")
  public Boolean isOutgoing() {
    return getOutgoingTransfer() != null;
  }
  
  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return getIncomingTransfers() != null && !getIncomingTransfers().isEmpty();
  }
  
  public BigInteger getIncomingAmount() {
    if (getIncomingTransfers() == null) return null;
    BigInteger incomingAmt = BigInteger.valueOf(0);
    for (MoneroTransfer transfer : this.getIncomingTransfers()) incomingAmt = incomingAmt.add(transfer.getAmount());
    return incomingAmt;
  }
  
  public BigInteger getOutgoingAmount() {
    return getOutgoingTransfer() != null ? getOutgoingTransfer().getAmount() : null;
  }
  
  @JsonManagedReference
  public List<MoneroIncomingTransfer> getIncomingTransfers() {
    return incomingTransfers;
  }
  
  public MoneroTxWallet setIncomingTransfers(List<MoneroIncomingTransfer> incomingTransfers) {
    this.incomingTransfers = incomingTransfers;
    return this;
  }
  
  @JsonManagedReference
  public MoneroOutgoingTransfer getOutgoingTransfer() {
    return outgoingTransfer;
  }
  
  public MoneroTxWallet setOutgoingTransfer(MoneroOutgoingTransfer outgoingTransfer) {
    this.outgoingTransfer = outgoingTransfer;
    return this;
  }
  
  /**
   * Returns a copy of this model's vouts as a list of type MoneroOutputWallet.
   * 
   * @return vouts of type MoneroOutputWallet
   */
  public List<MoneroOutputWallet> getVoutsWallet() {
    List<MoneroOutput> vouts = getVouts();
    if (vouts == null) return null;
    List<MoneroOutputWallet> voutsWallet = new ArrayList<MoneroOutputWallet>();
    for (MoneroOutput vout : getVouts()) {
      voutsWallet.add((MoneroOutputWallet) vout);
    }
    return voutsWallet;
  }
  
  /**
   * Set the tx's vouts (MoneroOutputWallet) which contain information relative
   * to a wallet.
   * 
   * Callers must cast to extended type (MoneroOutput) because Java
   * paramaterized types do not recognize inheritance.
   * 
   * @param vouts are MoneroOutputWallets to set for the wallet tx
   * @return MoneroTxWallet is a reference to this tx for chaining
   */
  public MoneroTxWallet setVouts(List<MoneroOutput> vouts) {
    
    // validate that all vouts are wallet outputs
    if (vouts != null) {
      for (MoneroOutput vout : vouts) {
        if (!(vout instanceof MoneroOutputWallet)) throw new MoneroException("Wallet transaction vouts must be of type MoneroOutputWallet");
      }
    }
    super.setVouts(vouts);
    return this;
  }
  
  /**
   * Set vouts with compile-time binding to MoneroOutputWallet for deserialization.
   * 
   * @param outputs are the tx's vouts
   * @return MoneroTxWallet is a reference to this tx for chaining
   */
  @JsonProperty("vouts")
  public MoneroTxWallet setVoutsWallet(List<MoneroOutputWallet> outputs) {
    return setVouts(new ArrayList<MoneroOutput>(outputs));
  }
  
  public String getNote() {
    return note;
  }
  
  public MoneroTxWallet setNote(String note) {
    this.note = note;
    return this;
  }
  
  public MoneroTxWallet merge(MoneroTx tx) {
    if (tx != null && !(tx instanceof MoneroTxWallet)) throw new MoneroException("Wallet transaction must be merged with type MoneroTxWallet");
    return merge((MoneroTxWallet) tx);
  }
  
  /**
   * Updates this transaction by merging the latest information from the given
   * transaction.
   * 
   * Merging can modify or build references to the transaction given so it
   * should not be re-used or it should be copied before calling this method.
   * 
   * @param tx is the transaction to merge into this transaction
   * @return this tx for chaining
   */
  public MoneroTxWallet merge(MoneroTxWallet tx) {
    if (!(tx instanceof MoneroTxWallet)) throw new MoneroException("Wallet transaction must be merged with type MoneroTxWallet");
    if (this == tx) return this;
    
    // merge base classes
    super.merge(tx);
    
    // merge tx set if they're different which comes back to merging txs
    if (txSet != tx.getTxSet()) {
      if (txSet == null) {
        txSet = new MoneroTxSet();
        txSet.setTxs(this);
      }
      if (tx.getTxSet() == null) {
        tx.setTxSet(new MoneroTxSet());
        tx.getTxSet().setTxs(tx);
      }
      txSet.merge(tx.getTxSet());
      return this;
    }
    
    // merge incoming transfers
    if (tx.getIncomingTransfers() != null) {
      if (this.getIncomingTransfers() == null) this.setIncomingTransfers(new ArrayList<MoneroIncomingTransfer>());
      for (MoneroIncomingTransfer transfer : tx.getIncomingTransfers()) {
        transfer.setTx(this);
        mergeIncomingTransfer(this.getIncomingTransfers(), transfer);
      }
    }
    
    // merge outgoing transfer
    if (tx.getOutgoingTransfer() != null) {
      tx.getOutgoingTransfer().setTx(this);
      if (this.getOutgoingTransfer() == null) this.setOutgoingTransfer(tx.getOutgoingTransfer());
      else this.getOutgoingTransfer().merge(tx.getOutgoingTransfer());
    }
    
    // merge simple extensions
    this.setNote(MoneroUtils.reconcile(this.getNote(), tx.getNote()));
    
    return this;  // for chaining
  }
  
  public String toString() {
    return toString(0, false);
  }
  
  public String toString(int indent) {
    return toString(indent, false);
  }
  
  public String toString(int indent, boolean oneLine) {
    StringBuilder sb = new StringBuilder();
    
    // represent tx with one line string
    // TODO: proper csv export
    if (oneLine) {
      sb.append(this.getId() + ", ");
      sb.append((this.isConfirmed() ? this.getBlock().getTimestamp() : this.getReceivedTimestamp()) + ", ");
      sb.append(this.isConfirmed() + ", ");
      sb.append((this.getOutgoingAmount() != null? this.getOutgoingAmount().toString() : "") + ", ");
      sb.append(this.getIncomingAmount() != null ? this.getIncomingAmount().toString() : "");
      return sb.toString();
    }
    
    // otherwise stringify all fields
    sb.append(super.toString(indent) + "\n");
    sb.append(MoneroUtils.kvLine("Is incoming", this.isIncoming(), indent));
    sb.append(MoneroUtils.kvLine("Incoming amount", this.getIncomingAmount(), indent));
    if (this.getIncomingTransfers() != null) {
      sb.append(MoneroUtils.kvLine("Incoming transfers", "", indent));
      for (int i = 0; i < this.getIncomingTransfers().size(); i++) {
        sb.append(MoneroUtils.kvLine(i + 1, "", indent + 1));
        sb.append(this.getIncomingTransfers().get(i).toString(indent + 2) + "\n");
      }
    }
    sb.append(MoneroUtils.kvLine("Is outgoing", this.isOutgoing(), indent));
    sb.append(MoneroUtils.kvLine("Outgoing amount", this.getOutgoingAmount(), indent));
    if (this.getOutgoingTransfer() != null) {
      sb.append(MoneroUtils.kvLine("Outgoing transfer", "", indent));
      sb.append(this.getOutgoingTransfer().toString(indent + 1) + "\n");
    }
    sb.append(MoneroUtils.kvLine("Note: ", this.getNote(), indent));
    String str = sb.toString();
    return str.substring(0, str.length() - 1);  // strip last newline
  }
  
  // private helper to merge transfers
  private static void mergeIncomingTransfer(List<MoneroIncomingTransfer> transfers, MoneroIncomingTransfer transfer) {
    for (MoneroIncomingTransfer aTransfer : transfers) {
      if (aTransfer.getAccountIndex() == transfer.getAccountIndex() && aTransfer.getSubaddressIndex() == transfer.getSubaddressIndex()) {
        aTransfer.merge(transfer);
        return;
      }
    }
    transfers.add(transfer);
  }
  
  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((incomingTransfers == null) ? 0 : incomingTransfers.hashCode());
    result = prime * result + ((note == null) ? 0 : note.hashCode());
    result = prime * result + ((outgoingTransfer == null) ? 0 : outgoingTransfer.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroTxWallet other = (MoneroTxWallet) obj;
    if (incomingTransfers == null) {
      if (other.incomingTransfers != null) return false;
    } else if (!incomingTransfers.equals(other.incomingTransfers)) return false;
    if (note == null) {
      if (other.note != null) return false;
    } else if (!note.equals(other.note)) return false;
    if (outgoingTransfer == null) {
      if (other.outgoingTransfer != null) return false;
    } else if (!outgoingTransfer.equals(other.outgoingTransfer)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public MoneroTxWallet setBlock(MoneroBlock block) {
    super.setBlock(block);
    return this;
  }

  @Override
  public MoneroTxWallet setId(String id) {
    super.setId(id);
    return this;
  }

  @Override
  public MoneroTxWallet setVersion(Integer version) {
    super.setVersion(version);
    return this;
  }

  @Override
  public MoneroTxWallet setIsMinerTx(Boolean isMinerTx) {
    super.setIsMinerTx(isMinerTx);
    return this;
  }

  @Override
  public MoneroTxWallet setPaymentId(String paymentId) {
    super.setPaymentId(paymentId);
    return this;
  }

  @Override
  public MoneroTxWallet setFee(BigInteger fee) {
    super.setFee(fee);
    return this;
  }

  @Override
  public MoneroTxWallet setMixin(Integer mixin) {
    super.setMixin(mixin);
    return this;
  }

  @Override
  public MoneroTxWallet setDoNotRelay(Boolean doNotRelay) {
    super.setDoNotRelay(doNotRelay);
    return this;
  }

  @Override
  public MoneroTxWallet setIsRelayed(Boolean isRelayed) {
    super.setIsRelayed(isRelayed);
    return this;
  }

  @Override
  public MoneroTxWallet setIsConfirmed(Boolean isConfirmed) {
    super.setIsConfirmed(isConfirmed);
    return this;
  }

  @Override
  public MoneroTxWallet setInTxPool(Boolean inTxPool) {
    super.setInTxPool(inTxPool);
    return this;
  }

  @Override
  public MoneroTxWallet setNumConfirmations(Long numConfirmations) {
    super.setNumConfirmations(numConfirmations);
    return this;
  }

  @Override
  public MoneroTxWallet setUnlockTime(Long unlockTime) {
    super.setUnlockTime(unlockTime);
    return this;
  }

  @Override
  public MoneroTxWallet setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    super.setLastRelayedTimestamp(lastRelayedTimestamp);
    return this;
  }

  @Override
  public MoneroTxWallet setReceivedTimestamp(Long receivedTimestamp) {
    super.setReceivedTimestamp(receivedTimestamp);
    return this;
  }

  @Override
  public MoneroTxWallet setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    super.setIsDoubleSpendSeen(isDoubleSpend);
    return this;
  }

  @Override
  public MoneroTxWallet setKey(String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public MoneroTxWallet setFullHex(String hex) {
    super.setFullHex(hex);
    return this;
  }

  @Override
  public MoneroTxWallet setPrunedHex(String prunedHex) {
    super.setPrunedHex(prunedHex);
    return this;
  }

  @Override
  public MoneroTxWallet setPrunableHex(String prunableHex) {
    super.setPrunableHex(prunableHex);
    return this;
  }

  @Override
  public MoneroTxWallet setPrunableHash(String prunableHash) {
    super.setPrunableHash(prunableHash);
    return this;
  }

  @Override
  public MoneroTxWallet setSize(Long size) {
    super.setSize(size);
    return this;
  }

  @Override
  public MoneroTxWallet setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }

  @Override
  public MoneroTxWallet setVins(List<MoneroOutput> vins) {
    super.setVins(vins);
    return this;
  }

  @Override
  public MoneroTxWallet setOutputIndices(List<Integer> outputIndices) {
    super.setOutputIndices(outputIndices);
    return this;
  }

  @Override
  public MoneroTxWallet setMetadata(String metadata) {
    super.setMetadata(metadata);
    return this;
  }

  @Override
  public MoneroTxWallet setExtra(int[] extra) {
    super.setExtra(extra);
    return this;
  }

  @Override
  public MoneroTxWallet setRctSignatures(Object rctSignatures) {
    super.setRctSignatures(rctSignatures);
    return this;
  }

  @Override
  public MoneroTxWallet setRctSigPrunable(Object rctSigPrunable) {
    super.setRctSigPrunable(rctSigPrunable);
    return this;
  }

  @Override
  public MoneroTxWallet setIsKeptByBlock(Boolean isKeptByBlock) {
    super.setIsKeptByBlock(isKeptByBlock);
    return this;
  }

  @Override
  public MoneroTxWallet setIsFailed(Boolean isFailed) {
    super.setIsFailed(isFailed);
    return this;
  }

  @Override
  public MoneroTxWallet setLastFailedHeight(Long lastFailedHeight) {
    super.setLastFailedHeight(lastFailedHeight);
    return this;
  }

  @Override
  public MoneroTxWallet setLastFailedId(String lastFailedId) {
    super.setLastFailedId(lastFailedId);
    return this;
  }

  @Override
  public MoneroTxWallet setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    super.setMaxUsedBlockHeight(maxUsedBlockHeight);
    return this;
  }

  @Override
  public MoneroTxWallet setMaxUsedBlockId(String maxUsedBlockId) {
    super.setMaxUsedBlockId(maxUsedBlockId);
    return this;
  }

  @Override
  public MoneroTxWallet setSignatures(List<String> signatures) {
    super.setSignatures(signatures);
    return this;
  }
}
