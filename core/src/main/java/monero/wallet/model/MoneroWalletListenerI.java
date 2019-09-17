package monero.wallet.model;

/**
 * Interface to receive wallet notifications.
 */
public interface MoneroWalletListenerI extends MoneroSyncListener {
  
  /**
   * Invoked when a new block is added to the chain.
   * 
   * @param height is the height of the block added to the chain
   */
  public void onNewBlock(long height);
  
  /**
   * Invoked when the wallet receives an output.
   * 
   * @param output is the incoming output to the wallet
   */
  public void onOutputReceived(MoneroOutputWallet output);
  
  /**
   * Invoked when the wallet spends an output.
   * 
   * @param output the outgoing transfer from the wallet
   */
  public void onOutputSpent(MoneroOutputWallet output);
}