package monero.wallet.model;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import common.types.Filter;
import common.utils.GenUtils;
import monero.daemon.model.MoneroBlock;
import monero.daemon.model.MoneroOutput;

/**
 * Configures a query to retrieve transactions.
 * 
 * All transactions are returned except those that do not meet the criteria defined in this query.
 */
public class MoneroTxQuery extends MoneroTxWallet implements Filter<MoneroTxWallet> {
  
  private Boolean isOutgoing;
  private Boolean isIncoming;
  private List<String> txIds;
  private Boolean hasPaymentId;
  private List<String> paymentIds;
  private Long height;
  private Long minHeight;
  private Long maxHeight;
  private Boolean includeOutputs;
  private MoneroTransferQuery transferQuery;
  private MoneroOutputQuery outputQuery;
  
  public MoneroTxQuery() {
    
  }
  
  public MoneroTxQuery(final MoneroTxQuery query) {
    super(query);
    this.isOutgoing = query.isOutgoing;
    this.isIncoming = query.isIncoming;
    if (query.txIds != null) this.txIds = new ArrayList<String>(query.txIds);
    this.hasPaymentId = query.hasPaymentId;
    if (query.paymentIds != null) this.paymentIds = new ArrayList<String>(query.paymentIds);
    this.height = query.height;
    this.minHeight = query.minHeight;
    this.maxHeight = query.maxHeight;
    this.includeOutputs = query.includeOutputs;
    if (query.transferQuery != null) {
      this.transferQuery = new MoneroTransferQuery(query.transferQuery);
      if (query.transferQuery.getTxQuery() == query) this.transferQuery.setTxQuery(this);
    }
    if (query.outputQuery != null) {
      this.outputQuery = new MoneroOutputQuery(query.outputQuery);
      if (query.outputQuery.getTxQuery() == query) this.outputQuery.setTxQuery(this) ;
    }
  }
  
  public MoneroTxQuery copy() {
    return new MoneroTxQuery(this);
  }
  
  @JsonProperty("isOutgoing")
  public Boolean isOutgoing() {
    return isOutgoing;
  }

  public MoneroTxQuery setIsOutgoing(Boolean isOutgoing) {
    this.isOutgoing = isOutgoing;
    return this;
  }

  @JsonProperty("isIncoming")
  public Boolean isIncoming() {
    return isIncoming;
  }

  public MoneroTxQuery setIsIncoming(Boolean isIncoming) {
    this.isIncoming = isIncoming;
    return this;
  }

  public List<String> getTxIds() {
    return txIds;
  }

  public MoneroTxQuery setTxIds(List<String> txIds) {
    this.txIds = txIds;
    return this;
  }
  
  public MoneroTxQuery setTxIds(String... txIds) {
    this.txIds = GenUtils.arrayToList(txIds);
    return this;
  }
  
  public MoneroTxQuery setTxId(String txId) {
    return setTxIds(Arrays.asList(txId));
  }

  @JsonProperty("hasPaymentId")
  public Boolean hasPaymentId() {
    return hasPaymentId;
  }

  public MoneroTxQuery setHasPaymentId(Boolean hasPaymentId) {
    this.hasPaymentId = hasPaymentId;
    return this;
  }

  public List<String> getPaymentIds() {
    return paymentIds;
  }

  public MoneroTxQuery setPaymentIds(List<String> paymentIds) {
    this.paymentIds = paymentIds;
    return this;
  }
  
  public MoneroTxQuery setPaymentId(String paymentId) {
    return setPaymentIds(Arrays.asList(paymentId));
  }
  
  public Long getHeight() {
    return height;
  }

  public MoneroTxQuery setHeight(Long height) {
    this.height = height;
    return this;
  }

  public Long getMinHeight() {
    return minHeight;
  }

  public MoneroTxQuery setMinHeight(Long minHeight) {
    this.minHeight = minHeight;
    return this;
  }

  public Long getMaxHeight() {
    return maxHeight;
  }

  public MoneroTxQuery setMaxHeight(Long maxHeight) {
    this.maxHeight = maxHeight;
    return this;
  }

  public Boolean getIncludeOutputs() {
    return includeOutputs;
  }

  public MoneroTxQuery setIncludeOutputs(Boolean includeOutputs) {
    this.includeOutputs = includeOutputs;
    return this;
  }

  public MoneroTransferQuery getTransferQuery() {
    return transferQuery;
  }

  public MoneroTxQuery setTransferQuery(MoneroTransferQuery transferQuery) {
    this.transferQuery = transferQuery;
    return this;
  }
  
  public MoneroOutputQuery getOutputQuery() {
    return outputQuery;
  }

  public MoneroTxQuery setOutputQuery(MoneroOutputQuery outputQuery) {
    this.outputQuery = outputQuery;
    return this;
  }
  
  @Override
  public boolean meetsCriteria(MoneroTxWallet tx) {
    if (tx == null) return false;
    
    // filter on tx
    if (this.getId() != null && !this.getId().equals(tx.getId())) return false;
    if (this.getPaymentId() != null && !this.getPaymentId().equals(tx.getPaymentId())) return false;
    if (this.isConfirmed() != null && this.isConfirmed() != tx.isConfirmed()) return false;
    if (this.inTxPool() != null && this.inTxPool() != tx.inTxPool()) return false;
    if (this.getDoNotRelay() != null && this.getDoNotRelay() != tx.getDoNotRelay()) return false;
    if (this.isRelayed() != null && this.isRelayed() != tx.isRelayed()) return false;
    if (this.isFailed() != null && this.isFailed() != tx.isFailed()) return false;
    if (this.isMinerTx() != null && this.isMinerTx() != tx.isMinerTx()) return false;
    
    // at least one transfer must meet transfer query if defined
    if (this.getTransferQuery() != null) {
      boolean matchFound = false;
      if (tx.getOutgoingTransfer() != null && this.getTransferQuery().meetsCriteria(tx.getOutgoingTransfer())) matchFound = true;
      else if (tx.getIncomingTransfers() != null) {
        for (MoneroTransfer incomingTransfer : tx.getIncomingTransfers()) {
          if (this.getTransferQuery().meetsCriteria(incomingTransfer)) {
            matchFound = true;
            break;
          }
        }
      }
      if (!matchFound) return false;
    }
    
    // at least one output must meet output query if defined
    if (this.getOutputQuery() != null && !this.getOutputQuery().isDefault()) {
      if (tx.getVouts() == null || tx.getVouts().isEmpty()) return false;
      boolean matchFound = false;
      for (MoneroOutputWallet vout : tx.getVoutsWallet()) {
        if (this.getOutputQuery().meetsCriteria(vout)) {
          matchFound = true;
          break;
        }
      }
      if (!matchFound) return false;
    }
    
    // filter on having a payment id
    if (this.hasPaymentId() != null) {
      if (this.hasPaymentId() && tx.getPaymentId() == null) return false;
      if (!this.hasPaymentId() && tx.getPaymentId() != null) return false;
    }
    
    // filter on incoming
    if (this.isIncoming() != null) {
      if (this.isIncoming() && !tx.isIncoming()) return false;
      if (!this.isIncoming() && tx.isIncoming()) return false;
    }
    
    // filter on outgoing
    if (this.isOutgoing() != null) {
      if (this.isOutgoing() && !tx.isOutgoing()) return false;
      if (!this.isOutgoing() && tx.isOutgoing()) return false;
    }
    
    // filter on remaining fields
    Long txHeight = tx.getBlock() == null ? null : tx.getBlock().getHeight();
    if (this.getTxIds() != null && !this.getTxIds().contains(tx.getId())) return false;
    if (this.getPaymentIds() != null && !this.getPaymentIds().contains(tx.getPaymentId())) return false;
    if (this.getHeight() != null && !this.getHeight().equals(txHeight)) return false;
    if (this.getMinHeight() != null && (txHeight == null || txHeight < this.getMinHeight())) return false;
    if (this.getMaxHeight() != null && (txHeight == null || txHeight > this.getMaxHeight())) return false;
    
    // transaction meets query criteria
    return true;
  }
  
  @Override
  public String toString() {
    throw new RuntimeException("Not implemented");
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------

  @Override
  public MoneroTxQuery setIncomingTransfers(List<MoneroIncomingTransfer> incomingTransfers) {
    super.setIncomingTransfers(incomingTransfers);
    return this;
  }

  @Override
  public MoneroTxQuery setOutgoingTransfer(MoneroOutgoingTransfer outgoingTransfer) {
    super.setOutgoingTransfer(outgoingTransfer);
    return this;
  }

  @Override
  public MoneroTxQuery setVouts(List<MoneroOutput> vouts) {
    super.setVouts(vouts);
    return this;
  }

  @Override
  public MoneroTxQuery setNote(String note) {
    super.setNote(note);
    return this;
  }

  @Override
  public MoneroTxQuery setBlock(MoneroBlock block) {
    super.setBlock(block);
    return this;
  }

  @Override
  public MoneroTxQuery setId(String id) {
    super.setId(id);
    return this;
  }

  @Override
  public MoneroTxQuery setVersion(Integer version) {
    super.setVersion(version);
    return this;
  }

  @Override
  public MoneroTxQuery setIsMinerTx(Boolean isMinerTx) {
    super.setIsMinerTx(isMinerTx);
    return this;
  }

  @Override
  public MoneroTxQuery setFee(BigInteger fee) {
    super.setFee(fee);
    return this;
  }

  @Override
  public MoneroTxQuery setMixin(Integer mixin) {
    super.setMixin(mixin);
    return this;
  }

  @Override
  public MoneroTxQuery setDoNotRelay(Boolean doNotRelay) {
    super.setDoNotRelay(doNotRelay);
    return this;
  }

  @Override
  public MoneroTxQuery setIsRelayed(Boolean isRelayed) {
    super.setIsRelayed(isRelayed);
    return this;
  }

  @Override
  public MoneroTxQuery setIsConfirmed(Boolean isConfirmed) {
    super.setIsConfirmed(isConfirmed);
    return this;
  }

  @Override
  public MoneroTxQuery setInTxPool(Boolean inTxPool) {
    super.setInTxPool(inTxPool);
    return this;
  }

  @Override
  public MoneroTxQuery setNumConfirmations(Long numConfirmations) {
    super.setNumConfirmations(numConfirmations);
    return this;
  }

  @Override
  public MoneroTxQuery setUnlockTime(Long unlockTime) {
    super.setUnlockTime(unlockTime);
    return this;
  }

  @Override
  public MoneroTxQuery setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    super.setLastRelayedTimestamp(lastRelayedTimestamp);
    return this;
  }

  @Override
  public MoneroTxQuery setReceivedTimestamp(Long receivedTimestamp) {
    super.setReceivedTimestamp(receivedTimestamp);
    return this;
  }

  @Override
  public MoneroTxQuery setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    super.setIsDoubleSpendSeen(isDoubleSpend);
    return this;
  }

  @Override
  public MoneroTxQuery setKey(String key) {
    super.setKey(key);
    return this;
  }

  @Override
  public MoneroTxQuery setFullHex(String hex) {
    super.setFullHex(hex);
    return this;
  }

  @Override
  public MoneroTxQuery setPrunedHex(String prunedHex) {
    super.setPrunedHex(prunedHex);
    return this;
  }

  @Override
  public MoneroTxQuery setPrunableHex(String prunableHex) {
    super.setPrunableHex(prunableHex);
    return this;
  }

  @Override
  public MoneroTxQuery setPrunableHash(String prunableHash) {
    super.setPrunableHash(prunableHash);
    return this;
  }

  @Override
  public MoneroTxQuery setSize(Long size) {
    super.setSize(size);
    return this;
  }

  @Override
  public MoneroTxQuery setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }

  @Override
  public MoneroTxQuery setVins(List<MoneroOutput> vins) {
    super.setVins(vins);
    return this;
  }

  @Override
  public MoneroTxQuery setOutputIndices(List<Integer> outputIndices) {
    super.setOutputIndices(outputIndices);
    return this;
  }

  @Override
  public MoneroTxQuery setMetadata(String metadata) {
    super.setMetadata(metadata);
    return this;
  }

  @Override
  public MoneroTxQuery setTxSet(MoneroTxSet commonTxSets) {
    super.setTxSet(commonTxSets);
    return this;
  }

  @Override
  public MoneroTxQuery setExtra(int[] extra) {
    super.setExtra(extra);
    return this;
  }

  @Override
  public MoneroTxQuery setRctSignatures(Object rctSignatures) {
    super.setRctSignatures(rctSignatures);
    return this;
  }

  @Override
  public MoneroTxQuery setRctSigPrunable(Object rctSigPrunable) {
    super.setRctSigPrunable(rctSigPrunable);
    return this;
  }

  @Override
  public MoneroTxQuery setIsKeptByBlock(Boolean isKeptByBlock) {
    super.setIsKeptByBlock(isKeptByBlock);
    return this;
  }

  @Override
  public MoneroTxQuery setIsFailed(Boolean isFailed) {
    super.setIsFailed(isFailed);
    return this;
  }

  @Override
  public MoneroTxQuery setLastFailedHeight(Long lastFailedHeight) {
    super.setLastFailedHeight(lastFailedHeight);
    return this;
  }

  @Override
  public MoneroTxQuery setLastFailedId(String lastFailedId) {
    super.setLastFailedId(lastFailedId);
    return this;
  }

  @Override
  public MoneroTxQuery setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    super.setMaxUsedBlockHeight(maxUsedBlockHeight);
    return this;
  }

  @Override
  public MoneroTxQuery setMaxUsedBlockId(String maxUsedBlockId) {
    super.setMaxUsedBlockId(maxUsedBlockId);
    return this;
  }

  @Override
  public MoneroTxQuery setSignatures(List<String> signatures) {
    super.setSignatures(signatures);
    return this;
  }
}
