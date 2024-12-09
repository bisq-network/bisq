package bisq.core;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;



import bisq.wallets.regtest.bitcoind.BitcoindRegtestSetup;

public class BitcoinjRegtestSetup {
    private final BitcoindRegtestSetup bitcoindRegtestSetup;

    public BitcoinjRegtestSetup(BitcoindRegtestSetup bitcoindRegtestSetup) {
        this.bitcoindRegtestSetup = bitcoindRegtestSetup;
    }

    public void fundWallet(Wallet wallet, double amount) throws InterruptedException {
        var walletReceivedLatch = new CountDownLatch(1);
        WalletCoinsReceivedEventListener coinsReceivedEventListener =
                (affectedWallet, tx, prevBalance, newBalance) -> {
                    walletReceivedLatch.countDown();
                };

        wallet.addCoinsReceivedEventListener(coinsReceivedEventListener);
        Coin previousBalance = wallet.getBalance();

        Address currentReceiveAddress = wallet.currentReceiveAddress();
        String address = currentReceiveAddress.toString();
        bitcoindRegtestSetup.fundAddress(address, amount);
        bitcoindRegtestSetup.mineOneBlock();

        boolean isSuccess = walletReceivedLatch.await(30, TimeUnit.SECONDS);
        wallet.removeCoinsReceivedEventListener(coinsReceivedEventListener);
        if (!isSuccess) {
            throw new IllegalStateException("Wallet not funded after 30 seconds.");
        }

        Coin balance = wallet.getBalance();
        Coin receivedAmount = balance.minus(previousBalance);

        long receivedAmountAsLong = receivedAmount.value;
        long fundedAmount = (long) amount * 100_000_000;
        if (receivedAmount.value != fundedAmount) {
            throw new IllegalStateException("Wallet balance is " + receivedAmountAsLong +
                    " but should be " + fundedAmount + ".");
        }
    }
}
