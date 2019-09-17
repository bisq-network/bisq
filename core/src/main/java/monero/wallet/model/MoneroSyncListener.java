package monero.wallet.model;

/**
 * Interface to receive progress notifications as a wallet is synchronized.
 */
public interface MoneroSyncListener {

  /**
   * Invoked as the wallet is synchronized.
   * 
   * @param height is the height of the synced block 
   * @param startHeight is the starting height of the sync request
   * @param endHeight is the ending height of the sync request
   * @param percentDone is the sync progress as a percentage
   * @param message is a human-readable description of the current progress
   */
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message);
}
