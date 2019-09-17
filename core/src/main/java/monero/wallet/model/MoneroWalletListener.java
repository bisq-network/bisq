package monero.wallet.model;

/**
 * Provides default handling for wallet notifications.
 */
public class MoneroWalletListener implements MoneroWalletListenerI {

  @Override
  public void onSyncProgress(long height, long startHeight, long endHeight, double percentDone, String message) { }

  @Override
  public void onNewBlock(long height) { }

  @Override
  public void onOutputReceived(MoneroOutputWallet output) { }

  @Override
  public void onOutputSpent(MoneroOutputWallet output) { }
}
