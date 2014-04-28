package io.bitsquare.btc;

import com.google.bitcoin.core.*;
import com.google.bitcoin.kits.WalletAppKit;
import com.google.bitcoin.params.MainNetParams;
import com.google.bitcoin.params.RegTestParams;
import com.google.bitcoin.script.Script;
import com.google.bitcoin.utils.Threading;
import com.google.inject.Inject;
import io.bitsquare.crypto.CryptoFacade;
import javafx.application.Platform;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * That facade delivers wallet functionality from the bitcoinJ library
 * Code from BitcoinJ must not be used outside that facade.
 * That way a change of the library will only affect that class.
 */
public class WalletFacade implements WalletEventListener
{
    public static final String MAIN_NET = "MAIN_NET";
    public static final String TEST_NET = "TEST_NET";

    private static final Logger log = LoggerFactory.getLogger(WalletFacade.class);

    private NetworkParameters networkParameters;
    private WalletAppKit walletAppKit;
    private CryptoFacade cryptoFacade;
    private BlockChainFacade blockChainFacade;

    // that wallet is used only for the registration process
    private AccountRegistrationWallet accountRegistrationWallet = null;

    private List<DownloadListener> downloadListeners = new ArrayList<>();
    private List<WalletListener> walletListeners = new ArrayList<>();

    @Inject
    public WalletFacade(NetworkParameters networkParameters, WalletAppKit walletAppKit, CryptoFacade cryptoFacade, BlockChainFacade blockChainFacade)
    {
        this.networkParameters = networkParameters;
        this.walletAppKit = walletAppKit;
        this.cryptoFacade = cryptoFacade;
        this.blockChainFacade = blockChainFacade;
    }

    public void initWallet()
    {
        // Tell bitcoinj to run event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.
        Threading.USER_THREAD = Platform::runLater;

        if (networkParameters == RegTestParams.get())
        {
            walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.
        }
        else if (networkParameters == MainNetParams.get())
        {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints"));
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        walletAppKit.setDownloadListener(new BlockChainDownloadListener())
                .setBlockingStartup(false)
                .setUserAgent("BitSquare", "1.0");
        walletAppKit.startAsync();
        walletAppKit.awaitRunning();
        // Don't make the user wait for confirmations for now, as the intention is they're sending it their own money!
        walletAppKit.wallet().allowSpendingUnconfirmedTransactions();
        walletAppKit.peerGroup().setMaxConnections(11);

        walletAppKit.wallet().addEventListener(this);

        log.info(walletAppKit.wallet().toString());
    }

    public void terminateWallet()
    {
        walletAppKit.stopAsync();
        walletAppKit.awaitTerminated();
    }

    public void addDownloadListener(DownloadListener listener)
    {
        downloadListeners.add(listener);
    }

    public void removeDownloadListener(DownloadListener listener)
    {
        downloadListeners.remove(listener);
    }

    public void addWalletListener(WalletListener listener)
    {
        walletListeners.add(listener);
    }

    public void removeWalletListener(WalletListener listener)
    {
        walletListeners.remove(listener);
    }

    public void addRegistrationWalletListener(WalletListener listener)
    {
        getAccountRegistrationWallet().addWalletListener(listener);
    }

    public void removeRegistrationWalletListener(WalletListener listener)
    {
        getAccountRegistrationWallet().removeWalletListener(listener);
    }


    //MOCK
    public KeyPair createNewAddress()
    {
        return new KeyPair(UUID.randomUUID().toString(), UUID.randomUUID().toString());
    }

    public BigInteger getBalance()
    {
        return walletAppKit.wallet().getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public String getAddress()
    {
        return walletAppKit.wallet().getKeys().get(0).toAddress(networkParameters).toString();
    }

    // account registration
    public Address getAccountRegistrationAddress()
    {
        return getAccountRegistrationWallet().getAddress();
    }

    public String getAccountRegistrationPubKey()
    {

        return Utils.bytesToHexString(getAccountRegistrationWallet().getKey().getPubKey());
    }

    public BigInteger getAccountRegistrationBalance()
    {
        return getAccountRegistrationWallet().getBalance(Wallet.BalanceType.ESTIMATED);
    }

    public void sendRegistrationTx(String stringifiedBankAccounts) throws InsufficientMoneyException
    {
        getAccountRegistrationWallet().saveToBlockchain(cryptoFacade.getEmbeddedAccountRegistrationData(getAccountRegistrationWallet().getKey(), stringifiedBankAccounts));
    }

    public boolean verifyAccountRegistration(String address, String hashAsHexStringToVerify, byte[] pubKey, String bankAccountIDs, String signatureBankAccountIDs)
    {
        return cryptoFacade.verifySignature(pubKey, bankAccountIDs, signatureBankAccountIDs)
                && cryptoFacade.verifyHash(hashAsHexStringToVerify, bankAccountIDs, signatureBankAccountIDs)
                && blockChainFacade.verifyAddressInBlockChain(hashAsHexStringToVerify, address);
    }

    public int getRegistrationConfirmationNumBroadcastPeers()
    {
        return getAccountRegistrationWallet().getConfirmationNumBroadcastPeers();
    }

    public int getRegistrationConfirmationDepthInBlocks()
    {
        return getAccountRegistrationWallet().getConfirmationDepthInBlocks();
    }

    // WalletEventListener
    @Override
    public void onCoinsReceived(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        for (WalletListener walletListener : walletListeners)
            walletListener.onCoinsReceived(newBalance);

        log.info("onCoinsReceived");
    }

    @Override
    public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx)
    {
        for (WalletListener walletListener : walletListeners)
            walletListener.onConfidenceChanged(tx.getConfidence().numBroadcastPeers(), tx.getConfidence().getDepthInBlocks());

        log.info("onTransactionConfidenceChanged " + tx.getConfidence().toString());
    }

    @Override
    public void onCoinsSent(Wallet wallet, Transaction tx, BigInteger prevBalance, BigInteger newBalance)
    {
        log.info("onCoinsSent");
    }

    @Override
    public void onReorganize(Wallet wallet)
    {
        log.info("onReorganize");
    }

    @Override
    public void onWalletChanged(Wallet wallet)
    {
        log.info("onWalletChanged");
    }

    @Override
    public void onKeysAdded(Wallet wallet, List<ECKey> keys)
    {
        log.info("onKeysAdded");
    }

    @Override
    public void onScriptsAdded(Wallet wallet, List<Script> scripts)
    {
        log.info("onScriptsAdded");
    }


    private AccountRegistrationWallet getAccountRegistrationWallet()
    {
        if (accountRegistrationWallet == null)
            accountRegistrationWallet = new AccountRegistrationWallet(networkParameters, walletAppKit.chain(), walletAppKit.peerGroup());

        return accountRegistrationWallet;
    }


    // inner classes
    private class BlockChainDownloadListener extends com.google.bitcoin.core.DownloadListener
    {
        @Override
        protected void progress(double percent, int blocksSoFar, Date date)
        {
            super.progress(percent, blocksSoFar, date);
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.progress(percent, blocksSoFar, date);
        }

        @Override
        protected void doneDownload()
        {
            super.doneDownload();
            for (DownloadListener downloadListener : downloadListeners)
                downloadListener.doneDownload();
        }
    }

    public static interface DownloadListener
    {
        void progress(double percent, int blocksSoFar, Date date);

        void doneDownload();
    }

    public static interface WalletListener
    {
        void onConfidenceChanged(int numBroadcastPeers, int depthInBlocks);

        void onCoinsReceived(BigInteger newBalance);
    }
}