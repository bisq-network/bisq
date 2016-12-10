/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.btc;

import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bitsquare.app.Log;
import io.bitsquare.btc.provider.fee.FeeService;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.handlers.ExceptionHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.network.Socks5ProxyProvider;
import io.bitsquare.storage.FileUtil;
import io.bitsquare.storage.Storage;
import io.bitsquare.user.Preferences;
import javafx.beans.property.*;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.DeterministicKey;
import org.bitcoinj.crypto.KeyCrypterScrypt;
import org.bitcoinj.net.discovery.SeedPeers;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongycastle.crypto.params.KeyParameter;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WalletSetup {
    private static final Logger log = LoggerFactory.getLogger(WalletSetup.class);

    private static final long STARTUP_TIMEOUT_SEC = 60;

    private final RegTestHost regTestHost;
    private final TradeWalletService tradeWalletService;
    private final AddressEntryList addressEntryList;
    private final UserAgent userAgent;
    private final Preferences preferences;
    private final FeeService feeService;
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final NetworkParameters params;
    private final File walletDir;
    private BitSquareWalletAppKit walletAppKit;
    private Wallet wallet;
    private Wallet tokenWallet;
    private String walletFileName = "Bitsquare";
    private String tokenWalletFileName = "SQU";
    private final Long bloomFilterTweak;
    private KeyParameter aesKey;
    private final Storage<Long> storage;
    public final BooleanProperty shutDownDone = new SimpleBooleanProperty();
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final ObjectProperty<List<Peer>> connectedPeers = new SimpleObjectProperty<>();
    private final DownloadListener downloadListener = new DownloadListener();
    // private final WalletEventListener walletEventListener = new BitsquareWalletEventListener();
    private List<Runnable> setupCompletedHandlers = new ArrayList<>();


    @Inject
    public WalletSetup(RegTestHost regTestHost,
                       TradeWalletService tradeWalletService,
                       AddressEntryList addressEntryList,
                       UserAgent userAgent,
                       Preferences preferences,
                       FeeService feeService,
                       Socks5ProxyProvider socks5ProxyProvider,
                       @Named(BtcOptionKeys.WALLET_DIR) File appDir) {

        this.regTestHost = regTestHost;
        this.tradeWalletService = tradeWalletService;
        this.addressEntryList = addressEntryList;
        this.userAgent = userAgent;
        this.preferences = preferences;
        this.feeService = feeService;
        this.socks5ProxyProvider = socks5ProxyProvider;

        params = preferences.getBitcoinNetwork().getParameters();
        walletDir = new File(appDir, "bitcoin");

        storage = new Storage<>(walletDir);
        Long persisted = storage.initAndGetPersistedWithFileName("BloomFilterNonce");
        if (persisted != null) {
            bloomFilterTweak = persisted;
        } else {
            bloomFilterTweak = new Random().nextLong();
            storage.queueUpForSave(bloomFilterTweak, 100);
        }
    }

    public void initialize(@Nullable DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        Log.traceCall();

        // Tell bitcoinj to execute event handlers on the JavaFX UI thread. This keeps things simple and means
        // we cannot forget to switch threads when adding event handlers. Unfortunately, the DownloadListener
        // we give to the app kit is currently an exception and runs on a library thread. It'll get fixed in
        // a future version.

        Threading.USER_THREAD = UserThread.getExecutor();

        Timer timeoutTimer = UserThread.runAfter(() ->
                exceptionHandler.handleException(new TimeoutException("Wallet did not initialize in " +
                        STARTUP_TIMEOUT_SEC + " seconds.")), STARTUP_TIMEOUT_SEC);

        backupWallets();

        final Socks5Proxy socks5Proxy = preferences.getUseTorForBitcoinJ() ? socks5ProxyProvider.getSocks5Proxy() : null;
        log.debug("Use socks5Proxy for bitcoinj: " + socks5Proxy);

        // If seed is non-null it means we are restoring from backup.
        walletAppKit = new BitSquareWalletAppKit(params, socks5Proxy, walletDir, walletFileName, tokenWalletFileName) {
            @Override
            protected void onSetupCompleted() {
                wallet = walletAppKit.wallet();
                tokenWallet = walletAppKit.tokenWallet();

                // Don't make the user wait for confirmations for now, as the intention is they're sending it
                // their own money!
                wallet.allowSpendingUnconfirmedTransactions();
                tokenWallet.allowSpendingUnconfirmedTransactions();

                if (params != RegTestParams.get())
                    walletAppKit.peerGroup().setMaxConnections(11);

                // wallet.addEventListener(walletEventListener);

                addressEntryList.onWalletReady(wallet);


                walletAppKit.peerGroup().addEventListener(new PeerEventListener() {
                    @Override
                    public void onPeersDiscovered(Set<PeerAddress> peerAddresses) {
                    }

                    @Override
                    public void onBlocksDownloaded(Peer peer, Block block, FilteredBlock filteredBlock, int blocksLeft) {
                    }

                    @Override
                    public void onChainDownloadStarted(Peer peer, int blocksLeft) {
                    }

                    @Override
                    public void onPeerConnected(Peer peer, int peerCount) {
                        numPeers.set(peerCount);
                        connectedPeers.set(walletAppKit.peerGroup().getConnectedPeers());
                    }

                    @Override
                    public void onPeerDisconnected(Peer peer, int peerCount) {
                        numPeers.set(peerCount);
                        connectedPeers.set(walletAppKit.peerGroup().getConnectedPeers());
                    }

                    @Override
                    public Message onPreMessageReceived(Peer peer, Message m) {
                        return null;
                    }

                    @Override
                    public void onTransaction(Peer peer, Transaction t) {
                    }

                    @Nullable
                    @Override
                    public List<Message> getData(Peer peer, GetDataMessage m) {
                        return null;
                    }
                });

                // set after wallet is ready
                tradeWalletService.setWalletAppKit(walletAppKit);
                tradeWalletService.setAddressEntryList(addressEntryList);
                timeoutTimer.stop();

                setupCompletedHandlers.stream().forEach(Runnable::run);

                // onSetupCompleted in walletAppKit is not the called on the last invocations, so we add a bit of delay
                UserThread.runAfter(resultHandler::handleResult, 100, TimeUnit.MILLISECONDS);
            }
        };

        // Bloom filters in BitcoinJ are completely broken
        // See: https://jonasnick.github.io/blog/2015/02/12/privacy-in-bitcoinj/
        // Here are a few improvements to fix a few vulnerabilities.

        // Bitsquare's BitcoinJ fork has added a bloomFilterTweak (nonce) setter to reuse the same seed avoiding the trivial vulnerability
        // by getting the real pub keys by intersections of several filters sent at each startup.
        walletAppKit.setBloomFilterTweak(bloomFilterTweak);

        // Avoid the simple attack (see: https://jonasnick.github.io/blog/2015/02/12/privacy-in-bitcoinj/) due to the 
        // default implementation using both pubkey and hash of pubkey. We have set a insertPubKey flag in BasicKeyChain to default false.

        // Default only 266 keys are generated (2 * 100+33). That would trigger new bloom filters when we are reaching 
        // the threshold. To avoid reaching the threshold we create much more keys which are unlikely to cause update of the
        // filter for most users. With lookaheadSize of 500 we get 1333 keys which should be enough for most users to 
        // never need to update a bloom filter, which would weaken privacy.
        walletAppKit.setLookaheadSize(500);

        // Calculation is derived from: https://www.reddit.com/r/Bitcoin/comments/2vrx6n/privacy_in_bitcoinj_android_wallet_multibit_hive/coknjuz
        // No. of false positives (56M keys in the blockchain): 
        // First attempt for FP rate:
        // FP rate = 0,0001;  No. of false positives: 0,0001 * 56 000 000  = 5600
        // We have 1333keys: 1333 / (5600 + 1333) = 0.19 -> 19 % probability that a pub key is in our wallet
        // After tests I found out that the bandwidth consumption varies widely related to the generated filter.
        // About 20- 40 MB for upload and 30-130 MB for download at first start up (spv chain).
        // Afterwards its about 1 MB for upload and 20-80 MB for download.
        // Probably better then a high FP rate would be to include foreign pubKeyHashes which are tested to not be used 
        // in many transactions. If we had a pool of 100 000 such keys (2 MB data dump) to random select 4000 we could mix it with our
        // 1000 own keys and get a similar probability rate as with the current setup but less variation in bandwidth 
        // consumption.

        // For now to reduce risks with high bandwidth consumption we reduce the FP rate by half.
        // FP rate = 0,00005;  No. of false positives: 0,00005 * 56 000 000  = 2800
        // 1333 / (2800 + 1333) = 0.32 -> 32 % probability that a pub key is in our wallet
        walletAppKit.setBloomFilterFalsePositiveRate(0.00005);

        String btcNodes = preferences.getBitcoinNodes();
        log.debug("btcNodes: " + btcNodes);
        boolean usePeerNodes = false;

        // Pass custom seed nodes if set in options
        if (!btcNodes.isEmpty()) {

            // TODO: this parsing should be more robust,
            // give validation error if needed.
            String[] nodes = btcNodes.replace(", ", ",").split(",");
            List<PeerAddress> peerAddressList = new ArrayList<>();
            for (String node : nodes) {
                String[] parts = node.split(":");
                if (parts.length == 1) {
                    // port not specified.  Use default port for network.
                    parts = new String[]{parts[0], Integer.toString(params.getPort())};
                }
                if (parts.length == 2) {
                    // note: this will cause a DNS request if hostname used.
                    // note: DNS requests are routed over socks5 proxy, if used.
                    // fixme: .onion hostnames will fail! see comments in SeedPeersSocks5Dns
                    InetSocketAddress addr;
                    if (socks5Proxy != null) {
                        InetSocketAddress unresolved = InetSocketAddress.createUnresolved(parts[0], Integer.parseInt(parts[1]));
                        // proxy remote DNS request happens here.
                        addr = SeedPeersSocks5Dns.lookup(socks5Proxy, unresolved);
                    } else {
                        // DNS request happens here. if it fails, addr.isUnresolved() == true.
                        addr = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                    }
                    // note: isUnresolved check should be removed once we fix PeerAddress
                    if (addr != null && !addr.isUnresolved())
                        peerAddressList.add(new PeerAddress(addr.getAddress(), addr.getPort()));
                }
            }
            if (peerAddressList.size() > 0) {
                PeerAddress peerAddressListFixed[] = new PeerAddress[peerAddressList.size()];
                log.debug("btcNodes parsed: " + Arrays.toString(peerAddressListFixed));

                walletAppKit.setPeerNodes(peerAddressList.toArray(peerAddressListFixed));
                usePeerNodes = true;
            }
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            if (regTestHost == RegTestHost.REG_TEST_SERVER) {
                try {
                    walletAppKit.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
                    usePeerNodes = true;
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            } else if (regTestHost == RegTestHost.LOCALHOST) {
                walletAppKit.connectToLocalHost();   // You should run a regtest mode bitcoind locally.}
            }
        } else if (params == MainNetParams.get()) {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            try {
                walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints"));
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.toString());
            }
        } else if (params == TestNet3Params.get()) {
            walletAppKit.setCheckpoints(getClass().getResourceAsStream("/wallet/checkpoints.testnet"));
        }

        // If operating over a proxy and we haven't set any peer nodes, then
        // we want to use SeedPeers for discovery instead of the default DnsDiscovery.
        // This is only because we do not yet have a Dns discovery class that works
        // reliably over proxy/tor.
        //
        // todo: There should be a user pref called "Use Local DNS for Proxy/Tor"
        // that disables this.  In that case, the default DnsDiscovery class will
        // be used which should work, but is less private.  The aim here is to
        // be private by default when using proxy/tor.  However, the seedpeers
        // could become outdated, so it is important that the user be able to
        // disable it, but should be made aware of the reduced privacy.
        if (socks5Proxy != null && !usePeerNodes) {
            // SeedPeersSocks5Dns should replace SeedPeers once working reliably.
            // SeedPeers uses hard coded stable addresses (from MainNetParams). It should be updated from time to time.
            walletAppKit.setDiscovery(new SeedPeers(params));
        }

        walletAppKit.setDownloadListener(downloadListener)
                .setBlockingStartup(false)
                .setUserAgent(userAgent.getName(), userAgent.getVersion())
                .restoreWalletFromSeed(seed);

        walletAppKit.addListener(new Service.Listener() {
            @Override
            public void failed(@NotNull Service.State from, @NotNull Throwable failure) {
                walletAppKit = null;
                log.error("walletAppKit failed");
                timeoutTimer.stop();
                UserThread.execute(() -> exceptionHandler.handleException(failure));
            }
        }, Threading.USER_THREAD);
        walletAppKit.startAsync();
    }

    public void shutDown() {
        if (walletAppKit != null) {
            try {
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated(5, TimeUnit.SECONDS);
            } catch (Throwable e) {
                // ignore
            }
            shutDownDone.set(true);
        }
    }

    public void backupWallets() {
        FileUtil.rollingBackup(walletDir, walletFileName + ".wallet", 20);
        FileUtil.rollingBackup(walletDir, tokenWalletFileName + ".wallet", 20);
    }

    public void clearBackup() {
        try {
            FileUtil.deleteDirectory(new File(Paths.get(walletDir.getAbsolutePath(), "backup").toString()));
        } catch (IOException e) {
            log.error("Could not delete directory " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void addSetupCompletedHandler(Runnable handler) {
        setupCompletedHandlers.add(handler);
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Wallet getTokenWallet() {
        return tokenWallet;
    }

    public NetworkParameters getParams() {
        return params;
    }

    public KeyParameter getAesKey() {
        return aesKey;
    }

    public BlockChain chain() {
        return walletAppKit.chain();
    }

    public ReadOnlyIntegerProperty numPeersProperty() {
        return numPeers;
    }

    public ReadOnlyObjectProperty<List<Peer>> connectedPeersProperty() {
        return connectedPeers;
    }

    public ReadOnlyDoubleProperty downloadPercentageProperty() {
        return downloadListener.percentageProperty();
    }

    public void setAesKey(KeyParameter aesKey) {
        this.aesKey = aesKey;
    }

    public void decryptWallets(@NotNull KeyParameter key) {
        wallet.decrypt(key);
        tokenWallet.decrypt(key);

        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair != null && keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.decrypt(key));
        });

        setAesKey(null);
        addressEntryList.queueUpForSave();
    }

    public void encryptWallets(KeyCrypterScrypt keyCrypterScrypt, KeyParameter key) {
        if (this.aesKey != null) {
            log.warn("encryptWallet called but we have a aesKey already set. " +
                    "We decryptWallet with the old key before we apply the new key.");
            decryptWallets(this.aesKey);
        }

        wallet.encrypt(keyCrypterScrypt, key);
        tokenWallet.encrypt(keyCrypterScrypt, key);

        addressEntryList.stream().forEach(e -> {
            final DeterministicKey keyPair = e.getKeyPair();
            if (keyPair != null && keyPair.isEncrypted())
                e.setDeterministicKey(keyPair.encrypt(keyCrypterScrypt, key));
        });
        setAesKey(key);
        addressEntryList.queueUpForSave();
    }

    public PeerGroup peerGroup() {
        return walletAppKit.peerGroup();
    }
  /*  public String exportWalletData(boolean includePrivKeys) {
        StringBuilder addressEntryListData = new StringBuilder();
        getAddressEntryListAsImmutableList().stream().forEach(e -> addressEntryListData.append(e.toString()).append("\n"));
        return "BitcoinJ wallet:\n" +
                wallet.toString(includePrivKeys, true, true, walletAppKit.chain()) + "\n\n" +
                "Bitsquare address entry list:\n" +
                addressEntryListData.toString() +
                "All pubkeys as hex:\n" +
                wallet.printAllPubKeysAsHex();
    }

    public String exportTokenWalletData(boolean includePrivKeys) {
        StringBuilder addressEntryListData = new StringBuilder();
        return "BitcoinJ SQU wallet:\n" +
                tokenWallet.toString(includePrivKeys, true, true, walletAppKit.chain()) + "\n\n" +
                "SQU address entry list:\n" +
                addressEntryListData.toString() +
                "All pubkeys as hex:\n" +
                tokenWallet.printAllPubKeysAsHex();
    }*/

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Inner classes
    ///////////////////////////////////////////////////////////////////////////////////////////

    private static class DownloadListener extends DownloadProgressTracker {
        private final DoubleProperty percentage = new SimpleDoubleProperty(-1);

        @Override
        protected void progress(double percentage, int blocksLeft, Date date) {
            super.progress(percentage, blocksLeft, date);
            UserThread.execute(() -> this.percentage.set(percentage / 100d));
        }

        @Override
        protected void doneDownload() {
            super.doneDownload();
            UserThread.execute(() -> this.percentage.set(1d));
        }

        public ReadOnlyDoubleProperty percentageProperty() {
            return percentage;
        }
    }


    /*private class BitsquareWalletEventListener extends AbstractWalletEventListener {
        @Override
        public void onCoinsReceived(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onCoinsSent(Wallet wallet, Transaction tx, Coin prevBalance, Coin newBalance) {
            notifyBalanceListeners(tx);
        }

        @Override
        public void onTransactionConfidenceChanged(Wallet wallet, Transaction tx) {
            for (AddressConfidenceListener addressConfidenceListener : addressConfidenceListeners) {
                List<TransactionConfidence> transactionConfidenceList = new ArrayList<>();
                transactionConfidenceList.add(getTransactionConfidence(tx, addressConfidenceListener.getAddress()));

                TransactionConfidence transactionConfidence = getMostRecentConfidence(transactionConfidenceList);
                addressConfidenceListener.onTransactionConfidenceChanged(transactionConfidence);
            }

            txConfidenceListeners.stream()
                    .filter(txConfidenceListener -> tx != null &&
                            tx.getHashAsString() != null &&
                            txConfidenceListener != null &&
                            tx.getHashAsString().equals(txConfidenceListener.getTxID()))
                    .forEach(txConfidenceListener ->
                            txConfidenceListener.onTransactionConfidenceChanged(tx.getConfidence()));
        }

        private void notifyBalanceListeners(Transaction tx) {
            for (BalanceListener balanceListener : balanceListeners) {
                Coin balance;
                if (balanceListener.getAddress() != null)
                    balance = getBalanceForAddress(balanceListener.getAddress());
                else
                    balance = getAvailableBalance();

                balanceListener.onBalanceChanged(balance, tx);
            }
        }
    }*/

    public void restoreBtcSeedWords(DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        Context ctx = Context.get();
        new Thread(() -> {
            try {
                Context.propagate(ctx);
                walletAppKit.stopAsync();
                walletAppKit.awaitTerminated();
                initialize(seed, resultHandler, exceptionHandler);
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }, "RestoreWallet-%d").start();
    }
}
