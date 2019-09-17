package monero.daemon.model;

/**
 * Receives notifications as a daemon is updated.
 */
public class MoneroDaemonListener {
  
  private MoneroBlockHeader lastHeader;

  /**
   * Called when a new block is added to the chain.
   * 
   * @param header is the header of the block added to the chain
   */
  public void onBlockHeader(MoneroBlockHeader header) {
    lastHeader = header;
  }
  
  /**
   * Get the last notified block header.
   * 
   * @return the last notified block header
   */
  public MoneroBlockHeader getLastBlockHeader() {
    return lastHeader;
  }
}
