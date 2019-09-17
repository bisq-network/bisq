package monero.daemon.model;

import static org.junit.Assert.assertNotNull;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
import com.fasterxml.jackson.annotation.JsonProperty;

import common.utils.GenUtils;
import monero.utils.MoneroUtils;

/**
 * Models a Monero block in the blockchain.
 */
public class MoneroBlock extends MoneroBlockHeader {

  private String hex;
  private MoneroTx minerTx;
  private List<MoneroTx> txs;
  private List<String> txIds;
  
  public MoneroBlock() {
    super();
  }
  
  public MoneroBlock(MoneroBlockHeader header) {
    super(header);
  }
  
  public MoneroBlock(MoneroBlock block) {
    super(block);
    this.hex = block.getHex();
    if (block.minerTx != null) this.minerTx = block.minerTx.copy().setBlock(this);
    if (block.txs != null) {
      this.txs = new ArrayList<MoneroTx>();
      for (MoneroTx tx : block.txs) txs.add(tx.copy().setBlock(this));
    }
    if (block.getTxIds() != null) this.txIds = new ArrayList<String>(block.getTxIds());
  }
  
  public String getHex() {
    return hex;
  }
  
  public MoneroBlock setHex(String hex) {
    this.hex = hex;
    return this;
  }
  
  public MoneroTx getMinerTx() {
    return minerTx;
  }
  
  public MoneroBlock setMinerTx(MoneroTx minerTx) {
    this.minerTx = minerTx;
    return this;
  }
  
  @JsonManagedReference("block_txs")
  public List<MoneroTx> getTxs() {
    return txs;
  }
  
  @JsonProperty("txs")
  public MoneroBlock setTxs(List<MoneroTx> txs) {
    this.txs = txs;
    return this;
  }
  
  @JsonIgnore
  public MoneroBlock setTxs(MoneroTx... txs) {
    this.txs = GenUtils.arrayToList(txs);
    return this;
  }
  
  public List<String> getTxIds() {
    return txIds;
  }
  
  public MoneroBlock setTxIds(List<String> txIds) {
    this.txIds = txIds;
    return this;
  }
  
  public MoneroBlock copy() {
    return new MoneroBlock(this);
  }
  
  public MoneroBlock merge(MoneroBlock block) {
    assertNotNull(block);
    if (this == block) return this;
    
    // merge header fields
    super.merge(block);
    
    // merge reconcilable block extensions
    this.setHex(MoneroUtils.reconcile(this.getHex(), block.getHex()));
    this.setTxIds(MoneroUtils.reconcile(this.getTxIds(), block.getTxIds()));
    
    // merge miner tx
    if (this.getMinerTx() == null) this.setMinerTx(block.getMinerTx());
    if (block.getMinerTx() != null) {
      block.getMinerTx().setBlock(this);
      minerTx.merge(block.getMinerTx());
    }
    
    // merge non-miner txs
    if (block.getTxs() != null) {
      for (MoneroTx tx : block.getTxs()) {
        tx.setBlock(this);
        MoneroUtils.mergeTx(txs, tx);
      }
    }

    return this;
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString(indent));
    sb.append("\n");
    sb.append(MoneroUtils.kvLine("Hex", getHex(), indent));
    sb.append(MoneroUtils.kvLine("Txs ids", getTxIds(), indent));
    if (getMinerTx() != null) {
      sb.append(MoneroUtils.kvLine("Miner tx", "", indent));
      sb.append(getMinerTx().toString(indent + 1) + "\n");
    }
    if (getTxs() != null) {
      sb.append(MoneroUtils.kvLine("Txs", "", indent));
      for (MoneroTx tx : getTxs()) {
        sb.append(tx.toString(indent + 1) + "\n");
      }
    }
    String str = sb.toString();
    return str.charAt(str.length() - 1) == '\n' ? str.substring(0, str.length() - 1) : str; // strip newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = super.hashCode();
    result = prime * result + ((minerTx == null) ? 0 : minerTx.hashCode());
    result = prime * result + ((hex == null) ? 0 : hex.hashCode());
    result = prime * result + ((txIds == null) ? 0 : txIds.hashCode());
    result = prime * result + ((txs == null) ? 0 : txs.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (!super.equals(obj)) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroBlock other = (MoneroBlock) obj;
    if (minerTx == null) {
      if (other.minerTx != null) return false;
    } else if (!minerTx.equals(other.minerTx)) return false;
    if (hex == null) {
      if (other.hex != null) return false;
    } else if (!hex.equals(other.hex)) return false;
    if (txIds == null) {
      if (other.txIds != null) return false;
    } else if (!txIds.equals(other.txIds)) return false;
    if (txs == null) {
      if (other.txs != null) return false;
    } else if (!txs.equals(other.txs)) return false;
    return true;
  }
  
  // ------------------- OVERRIDE CO-VARIANT RETURN TYPES ---------------------
  
  public MoneroBlock setId(String id) {
    super.setId(id);
    return this;
  }
  
  @Override
  public MoneroBlock setHeight(Long height) {
    super.setHeight(height);
    return this;
  }
  
  @Override
  public MoneroBlock setTimestamp(Long timestamp) {
    super.setTimestamp(timestamp);
    return this;
  }
  
  @Override
  public MoneroBlock setSize(Long size) {
    super.setSize(size);
    return this;
  }
  
  @Override
  public MoneroBlock setWeight(Long weight) {
    super.setWeight(weight);
    return this;
  }
  
  @Override
  public MoneroBlock setLongTermWeight(Long longTermWeight) {
    super.setLongTermWeight(longTermWeight);
    return this;
  }
  
  @Override
  public MoneroBlock setDepth(Long depth) {
    super.setDepth(depth);
    return this;
  }
  
  @Override
  public MoneroBlock setDifficulty(BigInteger difficulty) {
    super.setDifficulty(difficulty);
    return this;
  }
  
  @Override
  public MoneroBlock setCumulativeDifficulty(BigInteger cumulativeDifficulty) {
    super.setCumulativeDifficulty(cumulativeDifficulty);
    return this;
  }
  
  @Override
  public MoneroBlock setMajorVersion(Integer majorVersion) {
    super.setMajorVersion(majorVersion);
    return this;
  }
  
  @Override
  public MoneroBlock setMinorVersion(Integer minorVersion) {
    super.setMinorVersion(minorVersion);
    return this;
  }
  
  @Override
  public MoneroBlock setNonce(Integer nonce) {
    super.setNonce(nonce);
    return this;
  }
  
  @Override
  public MoneroBlock setNumTxs(Integer numTxs) {
    super.setNumTxs(numTxs);
    return this;
  }
  
  @Override
  public MoneroBlock setOrphanStatus(Boolean orphanStatus) {
    super.setOrphanStatus(orphanStatus);
    return this;
  }
  
  @Override
  public MoneroBlock setPrevId(String prevId) {
    super.setPrevId(prevId);
    return this;
  }
  
  @Override
  public MoneroBlock setReward(BigInteger reward) {
    super.setReward(reward);
    return this;
  }
  
  @Override
  public MoneroBlock setPowHash(String powHash) {
    super.setPowHash(powHash);
    return this;
  }
}
