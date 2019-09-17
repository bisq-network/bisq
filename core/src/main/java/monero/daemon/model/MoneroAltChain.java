package monero.daemon.model;

import java.math.BigInteger;
import java.util.List;

/**
 * Models an alternative chain seen by the node.
 */
public class MoneroAltChain {
  
  private List<String> blockIds;
  private BigInteger difficulty;
  private Long height;
  private Long length;
  private String mainChainParentBlockId;
  
  public List<String> getBlockIds() {
    return blockIds;
  }
  
  public void setBlockIds(List<String> blockIds) {
    this.blockIds = blockIds;
  }
  
  public BigInteger getDifficulty() {
    return difficulty;
  }
  
  public void setDifficulty(BigInteger difficulty) {
    this.difficulty = difficulty;
  }
  
  public Long getHeight() {
    return height;
  }
  
  public void setHeight(Long height) {
    this.height = height;
  }
  
  public Long getLength() {
    return length;
  }
  
  public void setLength(Long length) {
    this.length = length;
  }
  
  public String getMainChainParentBlockId() {
    return mainChainParentBlockId;
  }
  
  public void setMainChainParentBlockId(String mainChainParentBlockId) {
    this.mainChainParentBlockId = mainChainParentBlockId;
  }
}
