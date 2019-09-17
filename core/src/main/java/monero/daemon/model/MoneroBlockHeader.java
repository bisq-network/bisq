package monero.daemon.model;

import static org.junit.Assert.assertNotNull;

import java.math.BigInteger;

import monero.utils.MoneroUtils;

/**
 * Models a Monero block header which contains information about the block.
 */
public class MoneroBlockHeader {
  
  private String id;
  private Long height;
  private Long timestamp;
  private Long size;
  private Long weight;
  private Long longTermWeight;
  private Long depth;
  private BigInteger difficulty;
  private BigInteger cumulativeDifficulty;
  private Integer majorVersion;
  private Integer minorVersion;
  private Integer nonce;
  private String minerTxId;
  private Integer numTxs;
  private Boolean orphanStatus;
  private String prevId;
  private BigInteger reward;
  private String powHash;
  
  public MoneroBlockHeader() {
    super();
  }
  
  public MoneroBlockHeader(MoneroBlockHeader header) {
    this.id = header.id;
    this.height = header.height;
    this.timestamp = header.timestamp;
    this.size = header.size;
    this.weight = header.weight;
    this.longTermWeight = header.longTermWeight;
    this.depth = header.depth;
    this.difficulty = header.difficulty;
    this.cumulativeDifficulty = header.cumulativeDifficulty;
    this.majorVersion = header.majorVersion;
    this.minorVersion = header.minorVersion;
    this.nonce = header.nonce;
    this.numTxs = header.numTxs;
    this.orphanStatus = header.orphanStatus;
    this.prevId = header.prevId;
    this.reward = header.reward;
    this.powHash = header.powHash;
  }
  
  public String getId() {
    return id;
  }
  
  public MoneroBlockHeader setId(String id) {
    this.id = id;
    return this;
  }
  
  /**
   * Return the block's height which is the total number of blocks that have occurred before.
   * 
   * @return the block's height
   */
  public Long getHeight() {
    return height;
  }
  
  /**
   * Set the block's height which is the total number of blocks that have occurred before.
   * 
   * @param height is the block's height to set
   * @return a reference to this header for chaining
   */
  public MoneroBlockHeader setHeight(Long height) {
    this.height = height;
    return this;
  }
  
  public Long getTimestamp() {
    return timestamp;
  }
  
  public MoneroBlockHeader setTimestamp(Long timestamp) {
    this.timestamp = timestamp;
    return this;
  }
  
  public Long getSize() {
    return size;
  }
  
  public MoneroBlockHeader setSize(Long size) {
    this.size = size;
    return this;
  }
  
  public Long getWeight() {
    return weight;
  }
  
  public MoneroBlockHeader setWeight(Long weight) {
    this.weight = weight;
    return this;
  }
  
  public Long getLongTermWeight() {
    return longTermWeight;
  }
  
  public MoneroBlockHeader setLongTermWeight(Long longTermWeight) {
    this.longTermWeight = longTermWeight;
    return this;
  }
  
  public Long getDepth() {
    return depth;
  }
  
  public MoneroBlockHeader setDepth(Long depth) {
    this.depth = depth;
    return this;
  }
  
  public BigInteger getDifficulty() {
    return difficulty;
  }
  
  public MoneroBlockHeader setDifficulty(BigInteger difficulty) {
    this.difficulty = difficulty;
    return this;
  }
  
  public BigInteger getCumulativeDifficulty() {
    return cumulativeDifficulty;
  }
  
  public MoneroBlockHeader setCumulativeDifficulty(BigInteger cumulativeDifficulty) {
    this.cumulativeDifficulty = cumulativeDifficulty;
    return this;
  }
  
  public Integer getMajorVersion() {
    return majorVersion;
  }
  
  public MoneroBlockHeader setMajorVersion(Integer majorVersion) {
    this.majorVersion = majorVersion;
    return this;
  }
  
  public Integer getMinorVersion() {
    return minorVersion;
  }
  
  public MoneroBlockHeader setMinorVersion(Integer minorVersion) {
    this.minorVersion = minorVersion;
    return this;
  }
  
  public Integer getNonce() {
    return nonce;
  }
  
  public MoneroBlockHeader setNonce(Integer nonce) {
    this.nonce = nonce;
    return this;
  }
  
  public String getMinerTxId() {
    return minerTxId;
  }
  
  public MoneroBlockHeader setMinerTxId(String minerTxId) {
    this.minerTxId = minerTxId;
    return this;
  }
  
  public Integer getNumTxs() {
    return numTxs;
  }
  
  public MoneroBlockHeader setNumTxs(Integer numTxs) {
    this.numTxs = numTxs;
    return this;
  }
  
  public Boolean getOrphanStatus() {
    return orphanStatus;
  }
  
  public MoneroBlockHeader setOrphanStatus(Boolean orphanStatus) {
    this.orphanStatus = orphanStatus;
    return this;
  }
  
  public String getPrevId() {
    return prevId;
  }
  
  public MoneroBlockHeader setPrevId(String prevId) {
    this.prevId = prevId;
    return this;
  }
  
  public BigInteger getReward() {
    return reward;
  }
  
  public MoneroBlockHeader setReward(BigInteger reward) {
    this.reward = reward;
    return this;
  }
  
  public String getPowHash() {
    return powHash;
  }
  
  public MoneroBlockHeader setPowHash(String powHash) {
    this.powHash = powHash;
    return this;
  }
  
  public MoneroBlockHeader merge(MoneroBlockHeader header) {
    assertNotNull(header);
    if (this == header) return this;
    this.setId(MoneroUtils.reconcile(this.getId(), header.getId()));
    this.setHeight(MoneroUtils.reconcile(this.getHeight(), header.getHeight(), null, null, true));  // height can increase
    this.setTimestamp(MoneroUtils.reconcile(this.getTimestamp(), header.getTimestamp(), null, null, true));  // block timestamp can increase
    this.setSize(MoneroUtils.reconcile(this.getSize(), header.getSize()));
    this.setWeight(MoneroUtils.reconcile(this.getWeight(), header.getWeight()));
    this.setDepth(MoneroUtils.reconcile(this.getDepth(), header.getDepth()));
    this.setDifficulty(MoneroUtils.reconcile(this.getDifficulty(), header.getDifficulty()));
    this.setCumulativeDifficulty(MoneroUtils.reconcile(this.getCumulativeDifficulty(), header.getCumulativeDifficulty()));
    this.setMajorVersion(MoneroUtils.reconcile(this.getMajorVersion(), header.getMajorVersion()));
    this.setMinorVersion(MoneroUtils.reconcile(this.getMinorVersion(), header.getMinorVersion()));
    this.setNonce(MoneroUtils.reconcile(this.getNonce(), header.getNonce()));
    this.setMinerTxId(MoneroUtils.reconcile(this.getMinerTxId(), header.getMinerTxId()));
    this.setNumTxs(MoneroUtils.reconcile(this.getNumTxs(), header.getNumTxs()));
    this.setOrphanStatus(MoneroUtils.reconcile(this.getOrphanStatus(), header.getOrphanStatus()));
    this.setPrevId(MoneroUtils.reconcile(this.getPrevId(), header.getPrevId()));
    this.setReward(MoneroUtils.reconcile(this.getReward(), header.getReward()));
    this.setPowHash(MoneroUtils.reconcile(this.getPowHash(), header.getPowHash()));
    return this;
  }
  
  public String toString() {
    return toString(0);
  }
  
  public String toString(int indent) {
    StringBuilder sb = new StringBuilder();
    sb.append(MoneroUtils.kvLine("Id", getId(), indent));
    sb.append(MoneroUtils.kvLine("Height", getHeight(), indent));
    sb.append(MoneroUtils.kvLine("Timestamp", getTimestamp(), indent));
    sb.append(MoneroUtils.kvLine("Size", getSize(), indent));
    sb.append(MoneroUtils.kvLine("Weight", getWeight(), indent));
    sb.append(MoneroUtils.kvLine("Depth", getDepth(), indent));
    sb.append(MoneroUtils.kvLine("Difficulty", getDifficulty(), indent));
    sb.append(MoneroUtils.kvLine("Cumulative difficulty", getCumulativeDifficulty(), indent));
    sb.append(MoneroUtils.kvLine("Major version", getMajorVersion(), indent));
    sb.append(MoneroUtils.kvLine("Minor version", getMinorVersion(), indent));
    sb.append(MoneroUtils.kvLine("Nonce", getNonce(), indent));
    sb.append(MoneroUtils.kvLine("Miner tx id", getMinerTxId(), indent));
    sb.append(MoneroUtils.kvLine("Num txs", getNumTxs(), indent));
    sb.append(MoneroUtils.kvLine("Orphan status", getOrphanStatus(), indent));
    sb.append(MoneroUtils.kvLine("Prev id", getPrevId(), indent));
    sb.append(MoneroUtils.kvLine("Reward", getReward(), indent));
    sb.append(MoneroUtils.kvLine("Pow hash", getPowHash(), indent));
    String str = sb.toString();
    if (str.isEmpty()) return "";
    return str.charAt(str.length() - 1) == '\n' ? str.substring(0, str.length() - 1) : str; // strip newline
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((minerTxId == null) ? 0 : minerTxId.hashCode());
    result = prime * result + ((cumulativeDifficulty == null) ? 0 : cumulativeDifficulty.hashCode());
    result = prime * result + ((depth == null) ? 0 : depth.hashCode());
    result = prime * result + ((difficulty == null) ? 0 : difficulty.hashCode());
    result = prime * result + ((height == null) ? 0 : height.hashCode());
    result = prime * result + ((id == null) ? 0 : id.hashCode());
    result = prime * result + ((longTermWeight == null) ? 0 : longTermWeight.hashCode());
    result = prime * result + ((majorVersion == null) ? 0 : majorVersion.hashCode());
    result = prime * result + ((minorVersion == null) ? 0 : minorVersion.hashCode());
    result = prime * result + ((nonce == null) ? 0 : nonce.hashCode());
    result = prime * result + ((numTxs == null) ? 0 : numTxs.hashCode());
    result = prime * result + ((orphanStatus == null) ? 0 : orphanStatus.hashCode());
    result = prime * result + ((powHash == null) ? 0 : powHash.hashCode());
    result = prime * result + ((prevId == null) ? 0 : prevId.hashCode());
    result = prime * result + ((reward == null) ? 0 : reward.hashCode());
    result = prime * result + ((size == null) ? 0 : size.hashCode());
    result = prime * result + ((timestamp == null) ? 0 : timestamp.hashCode());
    result = prime * result + ((weight == null) ? 0 : weight.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    MoneroBlockHeader other = (MoneroBlockHeader) obj;
    if (minerTxId == null) {
      if (other.minerTxId != null) return false;
    } else if (!minerTxId.equals(other.minerTxId)) return false;
    if (cumulativeDifficulty == null) {
      if (other.cumulativeDifficulty != null) return false;
    } else if (!cumulativeDifficulty.equals(other.cumulativeDifficulty)) return false;
    if (depth == null) {
      if (other.depth != null) return false;
    } else if (!depth.equals(other.depth)) return false;
    if (difficulty == null) {
      if (other.difficulty != null) return false;
    } else if (!difficulty.equals(other.difficulty)) return false;
    if (height == null) {
      if (other.height != null) return false;
    } else if (!height.equals(other.height)) return false;
    if (id == null) {
      if (other.id != null) return false;
    } else if (!id.equals(other.id)) return false;
    if (longTermWeight == null) {
      if (other.longTermWeight != null) return false;
    } else if (!longTermWeight.equals(other.longTermWeight)) return false;
    if (majorVersion == null) {
      if (other.majorVersion != null) return false;
    } else if (!majorVersion.equals(other.majorVersion)) return false;
    if (minorVersion == null) {
      if (other.minorVersion != null) return false;
    } else if (!minorVersion.equals(other.minorVersion)) return false;
    if (nonce == null) {
      if (other.nonce != null) return false;
    } else if (!nonce.equals(other.nonce)) return false;
    if (numTxs == null) {
      if (other.numTxs != null) return false;
    } else if (!numTxs.equals(other.numTxs)) return false;
    if (orphanStatus == null) {
      if (other.orphanStatus != null) return false;
    } else if (!orphanStatus.equals(other.orphanStatus)) return false;
    if (powHash == null) {
      if (other.powHash != null) return false;
    } else if (!powHash.equals(other.powHash)) return false;
    if (prevId == null) {
      if (other.prevId != null) return false;
    } else if (!prevId.equals(other.prevId)) return false;
    if (reward == null) {
      if (other.reward != null) return false;
    } else if (!reward.equals(other.reward)) return false;
    if (size == null) {
      if (other.size != null) return false;
    } else if (!size.equals(other.size)) return false;
    if (timestamp == null) {
      if (other.timestamp != null) return false;
    } else if (!timestamp.equals(other.timestamp)) return false;
    if (weight == null) {
      if (other.weight != null) return false;
    } else if (!weight.equals(other.weight)) return false;
    return true;
  }
}
