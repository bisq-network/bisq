package monero.daemon.model;

import java.math.BigInteger;

/**
 * Monero block template to mine.
 */
public class MoneroBlockTemplate {
  
  private String blockTemplateBlob;
  private String blockHashingBlob;
  private BigInteger difficulty;
  private BigInteger expectedReward;
  private Long height;
  private String prevId;
  private Long reservedOffset;
  
  public String getBlockTemplateBlob() {
    return blockTemplateBlob;
  }
  
  public void setBlockTemplateBlob(String blockTemplateBlob) {
    this.blockTemplateBlob = blockTemplateBlob;
  }
  
  public String getBlockHashingBlob() {
    return blockHashingBlob;
  }
  
  public void setBlockHashingBlob(String blockHashingBlob) {
    this.blockHashingBlob = blockHashingBlob;
  }
  
  public BigInteger getDifficulty() {
    return difficulty;
  }
  
  public void setDifficulty(BigInteger difficulty) {
    this.difficulty = difficulty;
  }
  
  public BigInteger getExpectedReward() {
    return expectedReward;
  }
  
  public void setExpectedReward(BigInteger expectedReward) {
    this.expectedReward = expectedReward;
  }
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  public String getPrevId() {
    return prevId;
  }
  
  public void setPrevId(String prevId) {
    this.prevId = prevId;
  }
  
  public Long getReservedOffset() {
    return reservedOffset;
  }
  
  public void setReservedOffset(Long reservedOffset) {
    this.reservedOffset = reservedOffset;
  }
}
