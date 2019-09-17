package monero.daemon.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import common.utils.GenUtils;
import monero.utils.MoneroUtils;

/**
 * Represents a transaction on the Monero network.
 */
public class MoneroTx {
  
  public static final String DEFAULT_PAYMENT_ID = "0000000000000000";

  private MoneroBlock block;
  private String id;
  private Integer version;
  private Boolean isMinerTx;
  private String paymentId;
  private BigInteger fee;
  private Integer mixin;
  private Boolean doNotRelay;
  private Boolean isRelayed;
  private Boolean isConfirmed;
  private Boolean inTxPool;
  private Long numConfirmations;
  private Long unlockTime;
  private Long lastRelayedTimestamp;
  private Long receivedTimestamp;
  private Boolean isDoubleSpendSeen;
  private String key;
  private String fullHex;
  private String prunedHex;
  private String prunableHex;
  private String prunableHash;
  private Long size;
  private Long weight;
  private List<MoneroOutput> vins;
  private List<MoneroOutput> vouts;
  private List<Integer> outputIndices;
  private String metadata;
  private int[] extra;
  private Object rctSignatures; // TODO: implement
  private Object rctSigPrunable;  // TODO: implement
  private Boolean isKeptByBlock;
  private Boolean isFailed;
  private Long lastFailedHeight;
  private String lastFailedId;
  private Long maxUsedBlockHeight;
  private String maxUsedBlockId;
  private List<String> signatures;
  
  public MoneroTx() {
    // nothing to build
  }
  
  /**
   * Construct this transaction as a deep copy of the given transaction.
   * 
   * @param tx is the transaction to make a deep copy of
   */
  public MoneroTx(final MoneroTx tx) {
    this.id = tx.id;
    this.version = tx.version;
    this.isMinerTx = tx.isMinerTx;
    this.paymentId = tx.paymentId;
    this.fee = tx.fee;
    this.mixin = tx.mixin;
    this.doNotRelay = tx.doNotRelay;
    this.isRelayed = tx.isRelayed;
    this.isConfirmed = tx.isConfirmed;
    this.inTxPool = tx.inTxPool;
    this.numConfirmations = tx.numConfirmations;
    this.unlockTime = tx.unlockTime;
    this.lastRelayedTimestamp = tx.lastRelayedTimestamp;
    this.receivedTimestamp = tx.receivedTimestamp;
    this.isDoubleSpendSeen = tx.isDoubleSpendSeen;
    this.key = tx.key;
    this.fullHex = tx.fullHex;
    this.prunedHex = tx.prunedHex;
    this.prunableHex = tx.prunableHex;
    this.prunableHash = tx.prunableHash;
    this.size = tx.size;
    this.weight = tx.weight;
    if (tx.vins != null) {
      this.vins = new ArrayList<MoneroOutput>();
      for (MoneroOutput vin : tx.vins) vins.add(vin.copy().setTx(this));
    }
    if (tx.vouts != null) {
      this.vouts = new ArrayList<MoneroOutput>();
      for (MoneroOutput vout : tx.vouts) vouts.add(vout.copy().setTx(this));
    }
    if (tx.outputIndices != null) this.outputIndices = new ArrayList<Integer>(tx.outputIndices);
    this.metadata = tx.metadata;
    if (tx.extra != null) this.extra = tx.extra.clone();
    this.rctSignatures = tx.rctSignatures;
    this.rctSigPrunable = tx.rctSigPrunable;
    this.isKeptByBlock = tx.isKeptByBlock;
    this.isFailed = tx.isFailed;
    this.lastFailedHeight = tx.lastFailedHeight;
    this.lastFailedId = tx.lastFailedId;
    this.maxUsedBlockHeight = tx.maxUsedBlockHeight;
    this.maxUsedBlockId = tx.maxUsedBlockId;
    if (tx.signatures != null) this.signatures = new ArrayList<String>(tx.signatures);
  }
  
  public MoneroTx copy() {
    return new MoneroTx(this);
  }
  
  @JsonBackReference("block_txs")
  public MoneroBlock getBlock() {
    return block;
  }
  
  public MoneroTx setBlock(MoneroBlock block) {
    this.block = block;
    return this;
  }
  
  public Long getHeight() {
    return this.getBlock() == null ? null : this.getBlock().getHeight();
  }
  
  public String getId() {
    return id;
  }
  
  public MoneroTx setId(String id) {
    this.id = id;
    return this;
  }
  
  public Integer getVersion() {
    return version;
  }
  
  public MoneroTx setVersion(Integer version) {
    this.version = version;
    return this;
  }
  
  @JsonProperty("isMinerTx")
  public Boolean isMinerTx() {
    return isMinerTx;
  }
  
  public MoneroTx setIsMinerTx(Boolean isMinerTx) {
    this.isMinerTx = isMinerTx;
    return this;
  }
  
  public String getPaymentId() {
    return paymentId;
  }
  
  public MoneroTx setPaymentId(String paymentId) {
    this.paymentId = paymentId;
    return this;
  }
  
  public BigInteger getFee() {
    return fee;
  }
  
  public MoneroTx setFee(BigInteger fee) {
    this.fee = fee;
    return this;
  }
  
  public Integer getMixin() {
    return mixin;
  }
  
  public MoneroTx setMixin(Integer mixin) {
    this.mixin = mixin;
    return this;
  }
  
  public Boolean getDoNotRelay() {
    return doNotRelay;
  }
  
  public MoneroTx setDoNotRelay(Boolean doNotRelay) {
    this.doNotRelay = doNotRelay;
    return this;
  }
  
  @JsonProperty("isRelayed")
  public Boolean isRelayed() {
    return isRelayed;
  }
  
  public MoneroTx setIsRelayed(Boolean isRelayed) {
    this.isRelayed = isRelayed;
    return this;
  }
  
  @JsonProperty("isConfirmed")
  public Boolean isConfirmed() {
    return isConfirmed;
  }
  
  public MoneroTx setIsConfirmed(Boolean isConfirmed) {
    this.isConfirmed = isConfirmed;
    return this;
  }
  
  @JsonProperty("inTxPool")
  public Boolean inTxPool() {
    return inTxPool;
  }
  
  public MoneroTx setInTxPool(Boolean inTxPool) {
    this.inTxPool = inTxPool;
    return this;
  }
  
  public Long getNumConfirmations() {
    return numConfirmations;
  }
  
  public MoneroTx setNumConfirmations(Long numConfirmations) {
    this.numConfirmations = numConfirmations;
    return this;
  }
  
  public Long getUnlockTime() {
    return unlockTime;
  }
  
  public MoneroTx setUnlockTime(Long unlockTime) {
    this.unlockTime = unlockTime;
    return this;
  }
  
  public Long getLastRelayedTimestamp() {
    return lastRelayedTimestamp;
  }
  
  public MoneroTx setLastRelayedTimestamp(Long lastRelayedTimestamp) {
    this.lastRelayedTimestamp = lastRelayedTimestamp;
    return this;
  }
  
  public Long getReceivedTimestamp() {
    return receivedTimestamp;
  }
  
  public MoneroTx setReceivedTimestamp(Long receivedTimestamp) {
    this.receivedTimestamp = receivedTimestamp;
    return this;
  }
  
  @JsonProperty("isDoubleSpendSeen")
  public Boolean isDoubleSpendSeen() {
    return isDoubleSpendSeen;
  }
  
  public MoneroTx setIsDoubleSpendSeen(Boolean isDoubleSpend) {
    this.isDoubleSpendSeen = isDoubleSpend;
    return this;
  }
  
  public String getKey() {
    return key;
  }
  
  public MoneroTx setKey(String key) {
    this.key = key;
    return this;
  }
  
  public String getFullHex() {
    return fullHex;
  }
  
  public MoneroTx setFullHex(String fullHex) {
    this.fullHex = fullHex;
    return this;
  }
  
  public String getPrunedHex() {
    return prunedHex;
  }
  
  public MoneroTx setPrunedHex(String prunedHex) {
    this.prunedHex = prunedHex;
    return this;
  }
  
  public String getPrunableHex() {
    return prunableHex;
  }
  
  public MoneroTx setPrunableHex(String prunableHex) {
    this.prunableHex = prunableHex;
    return this;
  }
  
  public String getPrunableHash() {
    return prunableHash;
  }
  
  public MoneroTx setPrunableHash(String prunableHash) {
    this.prunableHash = prunableHash;
    return this;
  }
  
  public Long getSize() {
    return size;
  }
  
  public MoneroTx setSize(Long size) {
    this.size = size;
    return this;
  }
  
  public Long getWeight() {
    return weight;
  }
  
  public MoneroTx setWeight(Long weight) {
    this.weight = weight;
    return this;
  }
  
  @JsonManagedReference
  public List<MoneroOutput> getVins() {
    return vins;
  }
  
  public MoneroTx setVins(List<MoneroOutput> vins) {
    this.vins = vins;
    return this;
  }
  
  @JsonManagedReference
  public List<MoneroOutput> getVouts() {
    return vouts;
  }
  
  public MoneroTx setVouts(List<MoneroOutput> vouts) {
    this.vouts = vouts;
    return this;
  }
  
  public List<Integer> getOutputIndices() {
    return outputIndices;
  }
  
  public MoneroTx setOutputIndices(List<Integer> outputIndices) {
    this.outputIndices = outputIndices;
    return this;
  }
  
  public String getMetadata() {
    return metadata;
  }
  
  public MoneroTx setMetadata(String metadata) {
    this.metadata = metadata;
    return this;
  }
  
  public int[] getExtra() {
    return extra;
  }
  
  public MoneroTx setExtra(int[] extra) {
    this.extra = extra;
    return this;
  }
  
  public Object getRctSignatures() {
    return rctSignatures;
  }
  
  public MoneroTx setRctSignatures(Object rctSignatures) {
    this.rctSignatures = rctSignatures;
    return this;
  }
  
  public Object getRctSigPrunable() {
    return rctSigPrunable;
  }
  
  public MoneroTx setRctSigPrunable(Object rctSigPrunable) {
    this.rctSigPrunable = rctSigPrunable;
    return this;
  }
  
  @JsonProperty("isKeptByBlock")
  public Boolean isKeptByBlock() {
    return isKeptByBlock;
  }
  
  public MoneroTx setIsKeptByBlock(Boolean isKeptByBlock) {
    this.isKeptByBlock = isKeptByBlock;
    return this;
  }
  
  @JsonProperty("isFailed")
  public Boolean isFailed() {
    return isFailed;
  }
  
  public MoneroTx setIsFailed(Boolean isFailed) {
    this.isFailed = isFailed;
    return this;
  }
  
  public Long getLastFailedHeight() {
    return lastFailedHeight;
  }
  
  public MoneroTx setLastFailedHeight(Long lastFailedHeight) {
    this.lastFailedHeight = lastFailedHeight;
    return this;
  }
  
  public String getLastFailedId() {
    return lastFailedId;
  }
  
  public MoneroTx setLastFailedId(String lastFailedId) {
    this.lastFailedId = lastFailedId;
    return this;
  }
  
  public Long getMaxUsedBlockHeight() {
    return maxUsedBlockHeight;
  }
  
  public MoneroTx setMaxUsedBlockHeight(Long maxUsedBlockHeight) {
    this.maxUsedBlockHeight = maxUsedBlockHeight;
    return this;
  }
  
  public String getMaxUsedBlockId() {
    return maxUsedBlockId;
  }
  
  public MoneroTx setMaxUsedBlockId(String maxUsedBlockId) {
    this.maxUsedBlockId = maxUsedBlockId;
    return this;
  }
  
  public List<String> getSignatures() {
    return signatures;
  }
  
  public MoneroTx setSignatures(List<String> signatures) {
    this.signatures = signatures;
    return this;
  }
  
  public MoneroTx merge(MoneroTx tx) {
    if (this == tx) return this;
    
    // merge blocks if they're different which comes back to merging txs
    if (block != tx.getBlock()) {
      if (block == null) {
        block = new MoneroBlock();
        block.setTxs(this);
        block.setHeight(tx.getHeight());
      }
      if (tx.getBlock() == null) {
        tx.setBlock(new MoneroBlock());
        tx.getBlock().setTxs(tx);
        tx.getBlock().setHeight(getHeight());
      }
      block.merge(tx.getBlock());
      return this;
    }
    
    // otherwise merge tx fields
    this.setId(MoneroUtils.reconcile(this.getId(), tx.getId()));
    this.setVersion(MoneroUtils.reconcile(this.getVersion(), tx.getVersion()));
    this.setPaymentId(MoneroUtils.reconcile(this.getPaymentId(), tx.getPaymentId()));
    this.setFee(MoneroUtils.reconcile(this.getFee(), tx.getFee()));
    this.setMixin(MoneroUtils.reconcile(this.getMixin(), tx.getMixin()));
    this.setIsConfirmed(MoneroUtils.reconcile(this.isConfirmed(), tx.isConfirmed(), null, true, null));
    this.setDoNotRelay(MoneroUtils.reconcile(this.getDoNotRelay(), tx.getDoNotRelay(), null, false, null));  // tx can become relayed
    this.setIsRelayed(MoneroUtils.reconcile(this.isRelayed(), tx.isRelayed(), null, true, null));      // tx can become relayed
    this.setIsDoubleSpendSeen(MoneroUtils.reconcile(this.isDoubleSpendSeen(), tx.isDoubleSpendSeen()));
    this.setKey(MoneroUtils.reconcile(this.getKey(), tx.getKey()));
    this.setFullHex(MoneroUtils.reconcile(this.getFullHex(), tx.getFullHex()));
    this.setPrunedHex(MoneroUtils.reconcile(this.getPrunedHex(), tx.getPrunedHex()));
    this.setPrunableHex(MoneroUtils.reconcile(this.getPrunableHex(), tx.getPrunableHex()));
    this.setPrunableHash(MoneroUtils.reconcile(this.getPrunableHash(), tx.getPrunableHash()));
    this.setSize(MoneroUtils.reconcile(this.getSize(), tx.getSize()));
    this.setWeight(MoneroUtils.reconcile(this.getWeight(), tx.getWeight()));
    this.setOutputIndices(MoneroUtils.reconcile(this.getOutputIndices(), tx.getOutputIndices()));
    this.setMetadata(MoneroUtils.reconcile(this.getMetadata(), tx.getMetadata()));
    this.setExtra(MoneroUtils.reconcileIntArrays(this.getExtra(), tx.getExtra()));
    this.setRctSignatures(MoneroUtils.reconcile(this.getRctSignatures(), tx.getRctSignatures()));
    this.setRctSigPrunable(MoneroUtils.reconcile(this.getRctSigPrunable(), tx.getRctSigPrunable()));
    this.setIsKeptByBlock(MoneroUtils.reconcile(this.isKeptByBlock(), tx.isKeptByBlock()));
    this.setIsFailed(MoneroUtils.reconcile(this.isFailed(), tx.isFailed()));
    this.setLastFailedHeight(MoneroUtils.reconcile(this.getLastFailedHeight(), tx.getLastFailedHeight()));
    this.setLastFailedId(MoneroUtils.reconcile(this.getLastFailedId(), tx.getLastFailedId()));
    this.setMaxUsedBlockHeight(MoneroUtils.reconcile(this.getMaxUsedBlockHeight(), tx.getMaxUsedBlockHeight()));
    this.setMaxUsedBlockId(MoneroUtils.reconcile(this.getMaxUsedBlockId(), tx.getMaxUsedBlockId()));
    this.setSignatures(MoneroUtils.reconcile(this.getSignatures(), tx.getSignatures()));
    this.setUnlockTime(MoneroUtils.reconcile(this.getUnlockTime(), tx.getUnlockTime()));
    this.setNumConfirmations(MoneroUtils.reconcile(this.getNumConfirmations(), tx.getNumConfirmations(), null, null, true)); // num confirmations can increase
    
    // merge vins
    if (tx.getVins() != null) {
      for (MoneroOutput merger : tx.getVins()) {
        boolean merged = false;
        merger.setTx(this);
        if (this.getVins() == null) this.setVins(new ArrayList<MoneroOutput>());
        for (MoneroOutput mergee : this.getVins()) {
          if (mergee.getKeyImage().getHex().equals(merger.getKeyImage().getHex())) {
            mergee.merge(merger);
            merged = true;
            break;
          }
        }
        if (!merged) this.getVins().add(merger);
      }
    }
    
    // merge vouts
    if (tx.getVouts() != null) {
      for (MoneroOutput vout : tx.getVouts()) vout.setTx(this);
      if (this.getVouts() == null) this.setVouts(tx.getVouts());
      else {
        
        // validate output indices if present
        int numIndices = 0;
        for (MoneroOutput vout : this.getVouts()) if (vout.getIndex() != null) numIndices++;
        for (MoneroOutput vout : tx.getVouts()) if (vout.getIndex() != null) numIndices++;
        assertTrue("Some vouts have an output index and some do not", numIndices == 0 || this.getVouts().size() + tx.getVouts().size() == numIndices);
        
        // merge by output indices if present
        if (numIndices > 0) {
          for (MoneroOutput merger : tx.getVouts()) {
            boolean merged = false;
            merger.setTx(this);
            if (this.getVouts() == null) this.setVouts(new ArrayList<MoneroOutput>());
            for (MoneroOutput mergee : this.getVouts()) {
              if (mergee.getIndex().equals(merger.getIndex())) {
                mergee.merge(merger);
                merged = true;
                break;
              }
            }
            if (!merged) this.getVouts().add(merger);
          }
        } else {
          
          // determine if key images present
          int numKeyImages = 0;
          for (MoneroOutput vout : this.getVouts()) {
            if (vout.getKeyImage() != null) {
              assertNotNull(vout.getKeyImage().getHex());
              numKeyImages++;
            }
          }
          for (MoneroOutput vout : tx.getVouts()) {
            if (vout.getKeyImage() != null) {
              assertNotNull(vout.getKeyImage().getHex());
              numKeyImages++;
            }
          }
          assertTrue("Some vouts have a key image and some do not", numKeyImages == 0 || this.getVouts().size() + tx.getVouts().size() == numKeyImages);
          
          // merge by key images if present
          if (numKeyImages > 0) {
            for (MoneroOutput merger : tx.getVouts()) {
              boolean merged = false;
              merger.setTx(this);
              if (this.getVouts() == null) this.setVouts(new ArrayList<MoneroOutput>());
              for (MoneroOutput mergee : this.getVouts()) {
                if (mergee.getKeyImage().getHex().equals(merger.getKeyImage().getHex())) {
                  mergee.merge(merger);
                  merged = true;
                  break;
                }
              }
              if (!merged) this.getVouts().add(merger);
            }
          }

          // otherwise merge by position
          else {
            assertEquals(this.getVouts().size(), tx.getVouts().size());
            for (int i = 0; i < tx.getVouts().size(); i++) {
              this.getVouts().get(i).merge(tx.getVouts().get(i));
            }
          }
        }
      }
    }
    
    // handle unrelayed -> relayed -> confirmed
    if (this.isConfirmed()) {
      this.setInTxPool(false);
      this.setReceivedTimestamp(null);
      this.setLastRelayedTimestamp(null);
    } else {
      this.setInTxPool(MoneroUtils.reconcile(this.inTxPool(), tx.inTxPool(), null, true, null)); // unrelayed -> tx pool
      this.setReceivedTimestamp(MoneroUtils.reconcile(this.getReceivedTimestamp(), tx.getReceivedTimestamp(), null, null, false)); // take earliest receive time
      this.setLastRelayedTimestamp(MoneroUtils.reconcile(this.getLastRelayedTimestamp(), tx.getLastRelayedTimestamp(), null, null, true));  // take latest relay time
    }
    
    return this;  // for chaining
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(GenUtils.getIndent(indent) + "=== TX ===\n");
    sb.append(MoneroUtils.kvLine("Tx ID: ", getId(), indent));
    sb.append(MoneroUtils.kvLine("Height", getHeight(), indent));
    sb.append(MoneroUtils.kvLine("Version", getVersion(), indent));
    sb.append(MoneroUtils.kvLine("Is miner tx", isMinerTx(), indent));
    sb.append(MoneroUtils.kvLine("Payment ID", getPaymentId(), indent));
    sb.append(MoneroUtils.kvLine("Fee", getFee(), indent));
    sb.append(MoneroUtils.kvLine("Mixin", getMixin(), indent));
    sb.append(MoneroUtils.kvLine("Do not relay", getDoNotRelay(), indent));
    sb.append(MoneroUtils.kvLine("Is relayed", isRelayed(), indent));
    sb.append(MoneroUtils.kvLine("Is confirmed", isConfirmed(), indent));
    sb.append(MoneroUtils.kvLine("In tx pool", inTxPool(), indent));
    sb.append(MoneroUtils.kvLine("Num confirmations", getNumConfirmations(), indent));
    sb.append(MoneroUtils.kvLine("Unlock time", getUnlockTime(), indent));
    sb.append(MoneroUtils.kvLine("Last relayed time", getLastRelayedTimestamp(), indent));
    sb.append(MoneroUtils.kvLine("Received time", getReceivedTimestamp(), indent));
    sb.append(MoneroUtils.kvLine("Is double spend", isDoubleSpendSeen(), indent));
    sb.append(MoneroUtils.kvLine("Key", getKey(), indent));
    sb.append(MoneroUtils.kvLine("Full hex", getFullHex(), indent));
    sb.append(MoneroUtils.kvLine("Pruned hex", getPrunedHex(), indent));
    sb.append(MoneroUtils.kvLine("Prunable hex", getPrunableHex(), indent));
    sb.append(MoneroUtils.kvLine("Prunable hash", getPrunableHash(), indent));
    sb.append(MoneroUtils.kvLine("Size", getSize(), indent));
    sb.append(MoneroUtils.kvLine("Weight", getWeight(), indent));
    sb.append(MoneroUtils.kvLine("Output indices", getOutputIndices(), indent));
    sb.append(MoneroUtils.kvLine("Metadata", getMetadata(), indent));
    sb.append(MoneroUtils.kvLine("Extra", Arrays.toString(getExtra()), indent));
    sb.append(MoneroUtils.kvLine("RCT signatures", getRctSignatures(), indent));
    sb.append(MoneroUtils.kvLine("RCT sig prunable", getRctSigPrunable(), indent));
    sb.append(MoneroUtils.kvLine("Kept by block", isKeptByBlock(), indent));
    sb.append(MoneroUtils.kvLine("Is failed", isFailed(), indent));
    sb.append(MoneroUtils.kvLine("Last failed height", getLastFailedHeight(), indent));
    sb.append(MoneroUtils.kvLine("Last failed id", getLastFailedId(), indent));
    sb.append(MoneroUtils.kvLine("Max used block height", getMaxUsedBlockHeight(), indent));
    sb.append(MoneroUtils.kvLine("Max used block id", getMaxUsedBlockId(), indent));
    sb.append(MoneroUtils.kvLine("Signatures", getSignatures(), indent));
    if (getVins() != null) {
      sb.append(MoneroUtils.kvLine("Vins", "", indent));
      for (int i = 0; i < getVins().size(); i++) {
        sb.append(MoneroUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getVins().get(i).toString(indent + 2));
        sb.append('\n');
      }
    }
    if (getVouts() != null) {
      sb.append(MoneroUtils.kvLine("Vouts", "", indent));
      for (int i = 0; i < getVouts().size(); i++) {
        sb.append(MoneroUtils.kvLine(i + 1, "", indent + 1));
        sb.append(getVouts().get(i).toString(indent + 2));
        sb.append('\n');
      }
    }
    String str = sb.toString();
    return str.substring(0, str.length() - 1);
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((doNotRelay == null) ? 0 : doNotRelay.hashCode());
    result = prime * result + Arrays.hashCode(extra);
    result = prime * result + ((fee == null) ? 0 : fee.hashCode());
    result = prime * result + ((fullHex == null) ? 0 : fullHex.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((inTxPool == null) ? 0 : inTxPool.hashCode());
    result = prime * result + ((isMinerTx == null) ? 0 : isMinerTx.hashCode());
    result = prime * result + ((isConfirmed == null) ? 0 : isConfirmed.hashCode());
    result = prime * result + ((isDoubleSpendSeen == null) ? 0 : isDoubleSpendSeen.hashCode());
    result = prime * result + ((isFailed == null) ? 0 : isFailed.hashCode());
    result = prime * result + ((isKeptByBlock == null) ? 0 : isKeptByBlock.hashCode());
    result = prime * result + ((isRelayed == null) ? 0 : isRelayed.hashCode());
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + ((lastFailedHeight == null) ? 0 : lastFailedHeight.hashCode());
    result = prime * result + ((lastFailedId == null) ? 0 : lastFailedId.hashCode());
    result = prime * result + ((lastRelayedTimestamp == null) ? 0 : lastRelayedTimestamp.hashCode());
    result = prime * result + ((maxUsedBlockHeight == null) ? 0 : maxUsedBlockHeight.hashCode());
    result = prime * result + ((maxUsedBlockId == null) ? 0 : maxUsedBlockId.hashCode());
    result = prime * result + ((metadata == null) ? 0 : metadata.hashCode());
    result = prime * result + ((mixin == null) ? 0 : mixin.hashCode());
    result = prime * result + ((numConfirmations == null) ? 0 : numConfirmations.hashCode());
    result = prime * result + ((outputIndices == null) ? 0 : outputIndices.hashCode());
    result = prime * result + ((paymentId == null) ? 0 : paymentId.hashCode());
    result = prime * result + ((prunableHash == null) ? 0 : prunableHash.hashCode());
    result = prime * result + ((prunableHex == null) ? 0 : prunableHex.hashCode());
    result = prime * result + ((prunedHex == null) ? 0 : prunedHex.hashCode());
    result = prime * result + ((rctSigPrunable == null) ? 0 : rctSigPrunable.hashCode());
    result = prime * result + ((rctSignatures == null) ? 0 : rctSignatures.hashCode());
    result = prime * result + ((receivedTimestamp == null) ? 0 : receivedTimestamp.hashCode());
    result = prime * result + ((signatures == null) ? 0 : signatures.hashCode());
    result = prime * result + ((size == null) ? 0 : size.hashCode());
    result = prime * result + ((unlockTime == null) ? 0 : unlockTime.hashCode());
    result = prime * result + ((version == null) ? 0 : version.hashCode());
    result = prime * result + ((vins == null) ? 0 : vins.hashCode());
    result = prime * result + ((vouts == null) ? 0 : vouts.hashCode());
    result = prime * result + ((weight == null) ? 0 : weight.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroTx other = (MoneroTx) obj;
    if (doNotRelay == null) {
      if (other.doNotRelay != null) return false;
    } else if (!doNotRelay.equals(other.doNotRelay)) return false;
    if (!Arrays.equals(extra, other.extra)) return false;
    if (fee == null) {
      if (other.fee != null) return false;
    } else if (!fee.equals(other.fee)) return false;
    if (fullHex == null) {
      if (other.fullHex != null) return false;
    } else if (!fullHex.equals(other.fullHex)) return false;
    if (id == null) {
      if (other.id != null) return false;
    } else if (!id.equals(other.id)) return false;
    if (inTxPool == null) {
      if (other.inTxPool != null) return false;
    } else if (!inTxPool.equals(other.inTxPool)) return false;
    if (isMinerTx == null) {
      if (other.isMinerTx != null) return false;
    } else if (!isMinerTx.equals(other.isMinerTx)) return false;
    if (isConfirmed == null) {
      if (other.isConfirmed != null) return false;
    } else if (!isConfirmed.equals(other.isConfirmed)) return false;
    if (isDoubleSpendSeen == null) {
      if (other.isDoubleSpendSeen != null) return false;
    } else if (!isDoubleSpendSeen.equals(other.isDoubleSpendSeen)) return false;
    if (isFailed == null) {
      if (other.isFailed != null) return false;
    } else if (!isFailed.equals(other.isFailed)) return false;
    if (isKeptByBlock == null) {
      if (other.isKeptByBlock != null) return false;
    } else if (!isKeptByBlock.equals(other.isKeptByBlock)) return false;
    if (isRelayed == null) {
      if (other.isRelayed != null) return false;
    } else if (!isRelayed.equals(other.isRelayed)) return false;
    if (key == null) {
      if (other.key != null) return false;
    } else if (!key.equals(other.key)) return false;
    if (lastFailedHeight == null) {
      if (other.lastFailedHeight != null) return false;
    } else if (!lastFailedHeight.equals(other.lastFailedHeight)) return false;
    if (lastFailedId == null) {
      if (other.lastFailedId != null) return false;
    } else if (!lastFailedId.equals(other.lastFailedId)) return false;
    if (lastRelayedTimestamp == null) {
      if (other.lastRelayedTimestamp != null) return false;
    } else if (!lastRelayedTimestamp.equals(other.lastRelayedTimestamp)) return false;
    if (maxUsedBlockHeight == null) {
      if (other.maxUsedBlockHeight != null) return false;
    } else if (!maxUsedBlockHeight.equals(other.maxUsedBlockHeight)) return false;
    if (maxUsedBlockId == null) {
      if (other.maxUsedBlockId != null) return false;
    } else if (!maxUsedBlockId.equals(other.maxUsedBlockId)) return false;
    if (metadata == null) {
      if (other.metadata != null) return false;
    } else if (!metadata.equals(other.metadata)) return false;
    if (mixin == null) {
      if (other.mixin != null) return false;
    } else if (!mixin.equals(other.mixin)) return false;
    if (numConfirmations == null) {
      if (other.numConfirmations != null) return false;
    } else if (!numConfirmations.equals(other.numConfirmations)) return false;
    if (outputIndices == null) {
      if (other.outputIndices != null) return false;
    } else if (!outputIndices.equals(other.outputIndices)) return false;
    if (paymentId == null) {
      if (other.paymentId != null) return false;
    } else if (!paymentId.equals(other.paymentId)) return false;
    if (prunableHash == null) {
      if (other.prunableHash != null) return false;
    } else if (!prunableHash.equals(other.prunableHash)) return false;
    if (prunableHex == null) {
      if (other.prunableHex != null) return false;
    } else if (!prunableHex.equals(other.prunableHex)) return false;
    if (prunedHex == null) {
      if (other.prunedHex != null) return false;
    } else if (!prunedHex.equals(other.prunedHex)) return false;
    if (rctSigPrunable == null) {
      if (other.rctSigPrunable != null) return false;
    } else if (!rctSigPrunable.equals(other.rctSigPrunable)) return false;
    if (rctSignatures == null) {
      if (other.rctSignatures != null) return false;
    } else if (!rctSignatures.equals(other.rctSignatures)) return false;
    if (receivedTimestamp == null) {
      if (other.receivedTimestamp != null) return false;
    } else if (!receivedTimestamp.equals(other.receivedTimestamp)) return false;
    if (signatures == null) {
      if (other.signatures != null) return false;
    } else if (!signatures.equals(other.signatures)) return false;
    if (size == null) {
      if (other.size != null) return false;
    } else if (!size.equals(other.size)) return false;
    if (unlockTime == null) {
      if (other.unlockTime != null) return false;
    } else if (!unlockTime.equals(other.unlockTime)) return false;
    if (version == null) {
      if (other.version != null) return false;
    } else if (!version.equals(other.version)) return false;
    if (vins == null) {
      if (other.vins != null) return false;
    } else if (!vins.equals(other.vins)) return false;
    if (vouts == null) {
      if (other.vouts != null) return false;
    } else if (!vouts.equals(other.vouts)) return false;
    if (weight == null) {
      if (other.weight != null) return false;
    } else if (!weight.equals(other.weight)) return false;
    return true;
  }
}
