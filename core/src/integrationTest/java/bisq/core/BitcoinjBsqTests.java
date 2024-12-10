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
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.wallet.Wallet;

import java.nio.file.Path;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;



import bisq.wallets.regtest.BitcoindExtension;
import bisq.wallets.regtest.bitcoind.BitcoindRegtestSetup;

@ExtendWith(BitcoindExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Slf4j
public class BitcoinjBsqTests {

    private final BitcoindRegtestSetup bitcoindRegtestSetup;
    private final BitcoinjRegtestSetup bitcoinjRegtestSetup;
    private final BisqRegtestNetworkParams networkParams;
    private PeerGroup peerGroup;

    private final Wallet btcWallet;
    private Wallet bsqWallet;
    private final Wallet secondBsqWallet;
    private final Wallet emptyBsqWallet;

    private final BisqDefaultCoinSelector btcCoinSelector = new BisqDefaultCoinSelector(true) {
        @Override
        protected boolean isDustAttackUtxo(TransactionOutput output) {
            return false;
        }

        @Override
        protected boolean isTxOutputSpendable(TransactionOutput output) {
            return true;
        }
    };

    private BsqCoinSelector bsqCoinSelector;

    private BtcWalletV2 btcWalletV2;

    public BitcoinjBsqTests(BitcoindRegtestSetup bitcoindRegtestSetup) {
        this.bitcoindRegtestSetup = bitcoindRegtestSetup;
        bitcoinjRegtestSetup = new BitcoinjRegtestSetup(bitcoindRegtestSetup);
        networkParams = new BisqRegtestNetworkParams();
        networkParams.setPort(bitcoindRegtestSetup.getP2pPort());

        var walletFactory = new WalletFactory(networkParams);
        btcWallet = walletFactory.createBtcWallet();
        secondBsqWallet = walletFactory.createBsqWallet();
        emptyBsqWallet = walletFactory.createBsqWallet();
    }

    @BeforeAll
    void setup(@TempDir Path tempDir) throws InterruptedException {
        var wallets = List.of(btcWallet, secondBsqWallet);
        var regtestWalletAppKit = new RegtestWalletAppKit(networkParams, tempDir, wallets);
        regtestWalletAppKit.initialize();

        WalletAppKit walletAppKit = regtestWalletAppKit.getWalletAppKit();
        peerGroup = walletAppKit.peerGroup();
        bsqWallet = walletAppKit.wallet();

        bitcoinjRegtestSetup.fundWallet(bsqWallet, 1.0);
        bitcoinjRegtestSetup.fundWallet(btcWallet, 1.0);

        DaoStateService daoStateService = mock(DaoStateService.class);
        doReturn(true).when(daoStateService)
                .isTxOutputSpendable(any(TxOutputKey.class));

        bsqCoinSelector = new BsqCoinSelector(daoStateService, mock(UnconfirmedBsqChangeOutputListService.class));
        btcWalletV2 = new BtcWalletV2(btcCoinSelector, btcWallet);
    }

    @Test
    void sendBsqTest() throws InterruptedException, InsufficientMoneyException, BsqChangeBelowDustException {
        var bsqWalletV2 = new BsqWalletV2(networkParams,
                peerGroup,
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

        bitcoindRegtestSetup.mineOneBlock();

        boolean isSuccess = secondBsqWalletReceivedLatch.await(30, TimeUnit.SECONDS);
        assertThat("Didn't receive BSQ after 30 seconds.", isSuccess);

        assertEquals(bsqWallet.getBalance(), Coin.ofSat(99990000));
        assertEquals(btcWallet.getBalance(), Coin.ofSat(99999747));
        assertEquals(secondBsqWallet.getBalance(), Coin.ofSat(10000));
    }

    @Test
    void sendBsqButNotEnoughBsqTest() {
        var bsqWalletV2 = new BsqWalletV2(networkParams,
                peerGroup,
                btcWalletV2,
                emptyBsqWallet,
                bsqCoinSelector);

        var secondBsqWalletReceivedLatch = new CountDownLatch(1);
        bsqWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                secondBsqWalletReceivedLatch.countDown());

        // Send 100 BSQ (1 BSQ = 100 Satoshis)
        Address receiverAddress = bsqWallet.currentReceiveAddress();
        Coin receiverAmount = Coin.ofSat(100 * 100);

        assertThrows(InsufficientMoneyException.class, () ->
                bsqWalletV2.sendBsq(receiverAddress, receiverAmount, Coin.ofSat(10)));
    }

    @Test
    void sendMoreBsqThanInWalletTest() {
        var bsqWalletV2 = new BsqWalletV2(networkParams,
                peerGroup,
                btcWalletV2,
                bsqWallet,
                bsqCoinSelector);

        var secondBsqWalletReceivedLatch = new CountDownLatch(1);
        secondBsqWallet.addCoinsReceivedEventListener((wallet, tx, prevBalance, newBalance) ->
                secondBsqWalletReceivedLatch.countDown());

        Address receiverAddress = secondBsqWallet.currentReceiveAddress();
        Coin receiverAmount = bsqWallet.getBalance()
                .add(Coin.valueOf(100));

        assertThrows(InsufficientMoneyException.class, () ->
                bsqWalletV2.sendBsq(receiverAddress, receiverAmount, Coin.ofSat(10)));
    }
}
