package bisq.core;

import bisq.core.btc.exceptions.BsqChangeBelowDustException;
import bisq.core.btc.wallet.BisqDefaultCoinSelector;
import bisq.core.btc.wallet.BisqRegtestNetworkParams;
import bisq.core.btc.wallet.BsqCoinSelector;
import bisq.core.btc.wallet.BsqWalletV2;
import bisq.core.btc.wallet.BtcWalletV2;
import bisq.core.btc.wallet.WalletFactory;
import bisq.core.dao.state.DaoStateService;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.dao.state.unconfirmed.UnconfirmedBsqChangeOutputListService;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;

import java.nio.file.Path;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;



import bisq.wallets.regtest.BitcoindExtension;
import bisq.wallets.regtest.bitcoind.BitcoindRegtestSetup;

@ExtendWith(BitcoindExtension.class)
@Slf4j
public class BitcoinjBsqTests {

    private final BitcoindRegtestSetup regtestSetup;
    private final BisqRegtestNetworkParams networkParams;

    public BitcoinjBsqTests(BitcoindRegtestSetup regtestSetup) {
        this.regtestSetup = regtestSetup;
        networkParams = new BisqRegtestNetworkParams();
        networkParams.setPort(regtestSetup.getP2pPort());
    }

    @Test
    void sendBsqTest(@TempDir Path tempDir) throws InterruptedException, InsufficientMoneyException, BsqChangeBelowDustException {
        var walletFactory = new WalletFactory(networkParams);
        Wallet btcWallet = walletFactory.createBtcWallet();
        Wallet secondBsqWallet = walletFactory.createBsqWallet();

        var wallets = List.of(btcWallet, secondBsqWallet);
        var regtestWalletAppKit = new RegtestWalletAppKit(networkParams, tempDir, wallets);
        regtestWalletAppKit.initialize();

        WalletAppKit walletAppKit = regtestWalletAppKit.getWalletAppKit();
        Wallet bsqWallet = walletAppKit.wallet();

        var bsqWalletReceivedLatch = new CountDownLatch(1);
        bsqWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                bsqWalletReceivedLatch.countDown());

        var btcWalletReceivedLatch = new CountDownLatch(1);
        btcWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                btcWalletReceivedLatch.countDown());

        Address currentReceiveAddress = bsqWallet.currentReceiveAddress();
        String address = currentReceiveAddress.toString();
        regtestSetup.fundAddress(address, 1.0);

        currentReceiveAddress = btcWallet.currentReceiveAddress();
        address = currentReceiveAddress.toString();
        regtestSetup.fundAddress(address, 1.0);

        regtestSetup.mineOneBlock();

        boolean isSuccess = bsqWalletReceivedLatch.await(30, TimeUnit.SECONDS);
        assertThat("BSQ wallet not funded after 30 seconds.", isSuccess);

        Coin balance = bsqWallet.getBalance();
        assertThat("BitcoinJ BSQ wallet balance should equal 1 BTC.", balance.equals(Coin.COIN));

        isSuccess = btcWalletReceivedLatch.await(30, TimeUnit.SECONDS);
        assertThat("BTC wallet not funded after 30 seconds.", isSuccess);

        balance = btcWallet.getBalance();
        assertThat("BitcoinJ BTC wallet balance should equal 1 BTC.", balance.equals(Coin.COIN));

        DaoStateService daoStateService = mock(DaoStateService.class);
        doReturn(true).when(daoStateService)
                .isTxOutputSpendable(any(TxOutputKey.class));

        var bsqCoinSelector = new BsqCoinSelector(daoStateService, mock(UnconfirmedBsqChangeOutputListService.class));
        var btcCoinSelector = new BisqDefaultCoinSelector(true) {
            @Override
            protected boolean isDustAttackUtxo(TransactionOutput output) {
                return false;
            }

            @Override
            protected boolean isTxOutputSpendable(TransactionOutput output) {
                return true;
            }
        };

        var btcWalletV2 = new BtcWalletV2(btcCoinSelector, btcWallet);
        var bsqWalletV2 = new BsqWalletV2(networkParams,
                walletAppKit.peerGroup(),
                btcWalletV2,
                bsqWallet,
                bsqCoinSelector);

        var secondBsqWalletReceivedLatch = new CountDownLatch(1);
        secondBsqWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                secondBsqWalletReceivedLatch.countDown());

        // Send 100 BSQ (1 BSQ = 100 Satoshis)
        Address receiverAddress = secondBsqWallet.currentReceiveAddress();
        Coin receiverAmount = Coin.ofSat(100 * 100);
        bsqWalletV2.sendBsq(receiverAddress, receiverAmount, Coin.ofSat(10));

        regtestSetup.mineOneBlock();

        isSuccess = secondBsqWalletReceivedLatch.await(30, TimeUnit.SECONDS);
        assertThat("Didn't receive BSQ after 30 seconds.", isSuccess);

        assertEquals(bsqWallet.getBalance(), Coin.ofSat(99990000));
        assertEquals(btcWallet.getBalance(), Coin.ofSat(99999747));
        assertEquals(secondBsqWallet.getBalance(), Coin.ofSat(10000));
    }
}
