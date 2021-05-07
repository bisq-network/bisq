/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.btc.setup;

import bisq.core.btc.nodes.LocalBitcoinNode;
import bisq.core.btc.nodes.ProxySocketFactory;
import bisq.core.btc.wallet.BisqRiskAnalysis;

import bisq.common.config.Config;
import bisq.common.file.FileUtil;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.crypto.KeyCrypter;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.script.Script;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.KeyChainGroupStructure;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.io.Closeables;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.MoreExecutors;

import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;

import org.bouncycastle.crypto.params.KeyParameter;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;
import lombok.Setter;

import javax.annotation.Nullable;

import static bisq.common.util.Preconditions.checkDir;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

/**
 * <p>Utility class that wraps the boilerplate needed to set up a new SPV bitcoinj app. Instantiate it with a directory
 * and file prefix, optionally configure a few things, then use startAsync and optionally awaitRunning. The object will
 * construct and configure a {@link BlockChain}, {@link SPVBlockStore}, {@link Wallet} and {@link PeerGroup}.</p>
 *
 * <p>To add listeners and modify the objects that are constructed, you can either do that by overriding the
 * {@link #onSetupCompleted()} method (which will run on a background thread) and make your changes there,
 * or by waiting for the service to start and then accessing the objects from wherever you want. However, you cannot
 * access the objects this class creates until startup is complete.</p>
 *
 * <p>The asynchronous design of this class may seem puzzling (just use {@link #awaitRunning()} if you don't want that).
 * It is to make it easier to fit bitcoinj into GUI apps, which require a high degree of responsiveness on their main
 * thread which handles all the animation and user interaction. Even when blockingStart is false, initializing bitcoinj
 * means doing potentially blocking file IO, generating keys and other potentially intensive operations. By running it
 * on a background thread, there's no risk of accidentally causing UI lag.</p>
 *
 * <p>Note that {@link #awaitRunning()} can throw an unchecked {@link IllegalStateException}
 * if anything goes wrong during startup - you should probably handle it and use {@link Exception#getCause()} to figure
 * out what went wrong more precisely. Same thing if you just use the {@link #startAsync()} method.</p>
 */
public class WalletConfig extends AbstractIdleService {

    private static final int TOR_SOCKET_TIMEOUT = 120 * 1000;  // 1 sec used in bitcoinj, but since bisq uses Tor we allow more.
    private static final int TOR_VERSION_EXCHANGE_TIMEOUT = 125 * 1000;  // 5 sec used in bitcoinj, but since bisq uses Tor we allow more.

    protected static final Logger log = LoggerFactory.getLogger(WalletConfig.class);

    protected final NetworkParameters params;
    protected final String filePrefix;
    protected volatile BlockChain vChain;
    protected volatile SPVBlockStore vStore;
    protected volatile Wallet vBtcWallet;
    protected volatile Wallet vBsqWallet;
    protected volatile PeerGroup vPeerGroup;

    protected final File directory;
    protected volatile File vBtcWalletFile;
    protected volatile File vBsqWalletFile;

    protected PeerAddress[] peerAddresses;
    protected DownloadProgressTracker downloadListener;
    protected InputStream checkpoints;
    protected String userAgent, version;
    @Nullable
    protected DeterministicSeed restoreFromSeed;
    @Nullable
    protected PeerDiscovery discovery;

    protected volatile Context context;

    protected Config config;
    protected LocalBitcoinNode localBitcoinNode;
    protected Socks5Proxy socks5Proxy;
    protected int numConnectionsForBtc;
    @Getter
    @Setter
    private int minBroadcastConnections;
    @Getter
    private BooleanProperty migratedWalletToBtcSegwit = new SimpleBooleanProperty(false);
    @Getter
    private BooleanProperty migratedWalletToBsqSegwit = new SimpleBooleanProperty(false);
    @Getter
    private BooleanExpression migratedWalletToSegwit = migratedWalletToBtcSegwit.and(migratedWalletToBsqSegwit);

    /**
     * Creates a new WalletConfig, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public WalletConfig(NetworkParameters params, File directory, String filePrefix) {
        this(new Context(params), directory, filePrefix);
    }

    /**
     * Creates a new WalletConfig, with the given {@link Context}. Files will be stored in the given directory.
     */
    private WalletConfig(Context context, File directory, String filePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.directory = checkDir(directory);
        this.filePrefix = checkNotNull(filePrefix);
    }

    public WalletConfig setSocks5Proxy(Socks5Proxy socks5Proxy) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.socks5Proxy = socks5Proxy;
        return this;
    }

    public WalletConfig setConfig(Config config) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.config = config;
        return this;
    }

    public WalletConfig setLocalBitcoinNode(LocalBitcoinNode localBitcoinNode) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.localBitcoinNode = localBitcoinNode;
        return this;
    }

    public WalletConfig setNumConnectionsForBtc(int numConnectionsForBtc) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.numConnectionsForBtc = numConnectionsForBtc;
        return this;
    }


    /** Will only connect to the given addresses. Cannot be called after startup. */
    public WalletConfig setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
        return this;
    }

    /** Will only connect to localhost. Cannot be called after startup. */
    public WalletConfig connectToLocalHost() {
        final InetAddress localHost = InetAddress.getLoopbackAddress();
        return setPeerNodes(new PeerAddress(params, localHost, params.getPort()));
    }

    /**
     * If you want to learn about the sync process, you can provide a listener here. For instance, a
     * {@link DownloadProgressTracker} is a good choice.
     */
    public WalletConfig setDownloadListener(DownloadProgressTracker listener) {
        this.downloadListener = listener;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated with BuildCheckpoints. It makes initial
     * block sync faster for new users - please refer to the documentation on the bitcoinj website
     * (https://bitcoinj.github.io/speeding-up-chain-sync) for further details.
     */
    public WalletConfig setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Closeables.closeQuietly(checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version message.
     * @param userAgent A short string that should be the name of your app, e.g. "My Wallet"
     * @param version A short string that contains the version number, e.g. "1.0-BETA"
     */
    public WalletConfig setUserAgent(String userAgent, String version) {
        this.userAgent = checkNotNull(userAgent);
        this.version = checkNotNull(version);
        return this;
    }

    /**
     * If a seed is set here then any existing wallet that matches the file name will be renamed to a backup name,
     * the chain file will be deleted, and the wallet object will be instantiated with the given seed instead of
     * a fresh one being created. This is intended for restoring a wallet from the original seed. To implement restore
     * you would shut down the existing appkit, if any, then recreate it with the seed given by the user, then start
     * up the new kit. The next time your app starts it should work as normal (that is, don't keep calling this each
     * time).
     */
    public WalletConfig restoreWalletFromSeed(DeterministicSeed seed) {
        this.restoreFromSeed = seed;
        return this;
    }

    /**
     * Sets the peer discovery class to use. If none is provided then DNS is used, which is a reasonable default.
     */
    public WalletConfig setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
        return this;
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    protected void onSetupCompleted() {
        // Meant to be overridden by subclasses
    }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        try {
            File chainFile = new File(directory, filePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();
            String btcPrefix = "_BTC";
            vBtcWalletFile = new File(directory, filePrefix + btcPrefix + ".wallet");
            boolean shouldReplayWallet = (vBtcWalletFile.exists() && !chainFileExists) || restoreFromSeed != null;
            vBtcWallet = createOrLoadWallet(shouldReplayWallet, vBtcWalletFile, false);
            vBtcWallet.allowSpendingUnconfirmedTransactions();
            vBtcWallet.setRiskAnalyzer(new BisqRiskAnalysis.Analyzer());

            String bsqPrefix = "_BSQ";
            vBsqWalletFile = new File(directory, filePrefix + bsqPrefix + ".wallet");
            vBsqWallet = createOrLoadWallet(shouldReplayWallet, vBsqWalletFile, true);
            vBsqWallet.setRiskAnalyzer(new BisqRiskAnalysis.Analyzer());

            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = new SPVBlockStore(params, chainFile);
            if (!chainFileExists || restoreFromSeed != null) {
                if (checkpoints == null) {
                    checkpoints = CheckpointManager.openStream(params);
                }

                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;
                    if (restoreFromSeed != null) {
                        time = restoreFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation for restore.");
                            vStore.clear();
                        }
                    } else {
                        time = vBtcWallet.getEarliestKeyCreationTime();
                    }
                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Clearing the chain file in preparation for restore.");
                    vStore.clear();
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();
            if (minBroadcastConnections > 0)
                vPeerGroup.setMinBroadcastConnections(minBroadcastConnections);
            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                int maxConnections = Math.min(numConnectionsForBtc, peerAddresses.length);
                log.info("We try to connect to {} btc nodes", maxConnections);
                vPeerGroup.setMaxConnections(maxConnections);
                vPeerGroup.setAddPeersFromAddressMessage(false);
                peerAddresses = null;
            } else if (!params.getId().equals(NetworkParameters.ID_REGTEST)) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vBtcWallet);
            vPeerGroup.addWallet(vBtcWallet);
            vChain.addWallet(vBsqWallet);
            vPeerGroup.addWallet(vBsqWallet);
            onSetupCompleted();

            if (migratedWalletToSegwit.get()) {
                startPeerGroup();
            } else {
                migratedWalletToSegwit.addListener((observable, oldValue, newValue) -> {
                    if (newValue) {
                        startPeerGroup();
                    }
                });
            }

        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    private void startPeerGroup() {
        Futures.addCallback((ListenableFuture<?>) vPeerGroup.startAsync(), new FutureCallback<Object>() {
            @Override
            public void onSuccess(@Nullable Object result) {
                //completeExtensionInitiations(vPeerGroup);
                DownloadProgressTracker tracker = downloadListener == null ? new DownloadProgressTracker() : downloadListener;
                vPeerGroup.startBlockChainDownload(tracker);
            }

            @Override
            public void onFailure(Throwable t) {
                throw new RuntimeException(t);

            }
        }, MoreExecutors.directExecutor());
    }

    private Wallet createOrLoadWallet(boolean shouldReplayWallet,
                                      File walletFile,
                                      boolean isBsqWallet) throws Exception {
        Wallet wallet;

        maybeMoveOldWalletOutOfTheWay(walletFile);

        if (walletFile.exists()) {
            wallet = loadWallet(shouldReplayWallet, walletFile, isBsqWallet);
        } else {
            wallet = createWallet(isBsqWallet);
            //wallet.freshReceiveKey();

            // Currently the only way we can be sure that an extension is aware of its containing wallet is by
            // deserializing the extension (see WalletExtension#deserializeWalletExtension(Wallet, byte[]))
            // Hence, we first save and then load wallet to ensure any extensions are correctly initialized.
            wallet.saveToFile(walletFile);
            wallet = loadWallet(false, walletFile, isBsqWallet);
        }

        this.setupAutoSave(wallet, walletFile);

        return wallet;
    }

    protected void setupAutoSave(Wallet wallet, File walletFile) {
        wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);
    }

    private Wallet loadWallet(boolean shouldReplayWallet, File walletFile, boolean isBsqWallet) throws Exception {
        Wallet wallet;
        try (FileInputStream walletStream = new FileInputStream(walletFile)) {
            WalletExtension[] extArray = new WalletExtension[]{};
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            serializer = new WalletProtobufSerializer();
            // Hack to convert bitcoinj 0.14 wallets to bitcoinj 0.15 format
            serializer.setKeyChainFactory(new BisqKeyChainFactory(isBsqWallet));
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
            maybeAddSegwitKeychain(wallet, null, isBsqWallet);
        }
        return wallet;
    }

    protected Wallet createWallet(boolean isBsqWallet) {
        Script.ScriptType preferredOutputScriptType = Script.ScriptType.P2WPKH;
        KeyChainGroupStructure structure = new BisqKeyChainGroupStructure(isBsqWallet);
        KeyChainGroup.Builder kcgBuilder = KeyChainGroup.builder(params, structure);
        if (restoreFromSeed != null) {
            kcgBuilder.fromSeed(restoreFromSeed, preferredOutputScriptType);
        } else {
            // new wallet
            if (!isBsqWallet) {
                // btc wallet uses a new random seed.
                kcgBuilder.fromRandom(preferredOutputScriptType);
            } else {
                // bsq wallet uses btc wallet's seed created a few milliseconds ago.
                kcgBuilder.fromSeed(vBtcWallet.getKeyChainSeed(), preferredOutputScriptType);
            }
        }
        return new Wallet(params, kcgBuilder.build());
    }

    private void maybeMoveOldWalletOutOfTheWay(File walletFile) {
        if (restoreFromSeed == null) return;
        if (!walletFile.exists()) return;
        int counter = 1;
        File newName;
        do {
            newName = new File(walletFile.getParent(), "Backup " + counter + " for " + walletFile.getName());
            counter++;
        } while (newName.exists());
        log.info("Renaming old wallet file {} to {}", walletFile, newName);
        if (!walletFile.renameTo(newName)) {
            // This should not happen unless something is really messed up.
            throw new RuntimeException("Failed to rename wallet for restore");
        }
    }

    private PeerGroup createPeerGroup() {
        PeerGroup peerGroup;
        // no proxy case.
        if (socks5Proxy == null) {
            peerGroup = new PeerGroup(params, vChain);
        } else {
            // proxy case (tor).
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(socks5Proxy.getInetAddress(), socks5Proxy.getPort()));

            ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
            // We don't use tor mode if we have a local node running
            BlockingClientManager blockingClientManager = localBitcoinNode.shouldBeUsed() ?
                    new BlockingClientManager() :
                    new BlockingClientManager(proxySocketFactory);

            peerGroup = new PeerGroup(params, vChain, blockingClientManager);

            blockingClientManager.setConnectTimeoutMillis(TOR_SOCKET_TIMEOUT);
            peerGroup.setConnectTimeoutMillis(TOR_VERSION_EXCHANGE_TIMEOUT);
        }

        if (!localBitcoinNode.shouldBeUsed())
            peerGroup.setUseLocalhostPeerWhenPossible(false);

        return peerGroup;
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);

            vBtcWallet.saveToFile(vBtcWalletFile);
            vBtcWallet = null;
            log.info("BtcWallet saved to file");

            if (vBsqWallet != null && vBsqWalletFile != null) {
                vBsqWallet.saveToFile(vBsqWalletFile);
                vBsqWallet = null;
                log.info("BsqWallet saved to file");
            }

            vStore.close();
            vStore = null;
            log.info("SPV file closed");

            vChain = null;

            // vPeerGroup.stop has no timeout and can take very long (10 sec. in my test). So we call it at the end.
            // We might get likely interrupted by the parent call timeout.
            if (vPeerGroup.isRunning()) {
                vPeerGroup.stop();
                log.info("PeerGroup stopped");
            } else {
                log.info("PeerGroup not stopped because it was not running");
            }
            vPeerGroup = null;
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    public NetworkParameters params() {
        return params;
    }

    public BlockChain chain() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vChain;
    }

    public BlockStore store() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vStore;
    }

    public Wallet btcWallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vBtcWallet;
    }

    public Wallet bsqWallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vBsqWallet;
    }

    public PeerGroup peerGroup() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vPeerGroup;
    }

    public File directory() {
        return directory;
    }

    public void maybeAddSegwitKeychain(Wallet wallet, KeyParameter aesKey, boolean isBsqWallet) {
        var nonSegwitAccountPath = isBsqWallet
                ? BisqKeyChainGroupStructure.BIP44_BSQ_NON_SEGWIT_ACCOUNT_PATH
                : BisqKeyChainGroupStructure.BIP44_BTC_NON_SEGWIT_ACCOUNT_PATH;
        var preSegwitBackupFilename = isBsqWallet
                ? WalletsSetup.PRE_SEGWIT_BSQ_WALLET_BACKUP
                : WalletsSetup.PRE_SEGWIT_BTC_WALLET_BACKUP;
        var walletFilename = isBsqWallet ? "bisq_BSQ.wallet" : "bisq_BTC.wallet";

        if (nonSegwitAccountPath.equals(wallet.getActiveKeyChain().getAccountPath())) {
            if (wallet.isEncrypted() && aesKey == null) {
                // wait for the aesKey to be set and this method to be invoked again.
                return;
            }
            // Do a backup of the wallet
            File backup = new File(directory, preSegwitBackupFilename);
            try {
                FileUtil.copyFile(new File(directory, walletFilename), backup);
            } catch (IOException e) {
                log.error(e.toString(), e);
            }

            // Wallet does not have a native segwit keychain, we should add one.
            DeterministicSeed seed = wallet.getKeyChainSeed();
            if (aesKey != null) {
                // If wallet is encrypted, decrypt the seed.
                KeyCrypter keyCrypter = wallet.getKeyCrypter();
                seed = seed.decrypt(keyCrypter, DeterministicKeyChain.DEFAULT_PASSPHRASE_FOR_MNEMONIC, aesKey);
            }
            DeterministicKeyChain nativeSegwitKeyChain = DeterministicKeyChain.builder().seed(seed)
                    .outputScriptType(Script.ScriptType.P2WPKH)
                    .accountPath(new BisqKeyChainGroupStructure(isBsqWallet).accountPathFor(Script.ScriptType.P2WPKH)).build();
            if (aesKey != null) {
                // If wallet is encrypted, encrypt the new keychain.
                KeyCrypter keyCrypter = wallet.getKeyCrypter();
                nativeSegwitKeyChain = nativeSegwitKeyChain.toEncrypted(keyCrypter, aesKey);
            }
            wallet.addAndActivateHDChain(nativeSegwitKeyChain);
        }
        if (isBsqWallet) {
            migratedWalletToBsqSegwit.set(true);
        } else {
            migratedWalletToBtcSegwit.set(true);
        }
    }

    public boolean stateStartingOrRunning() {
        return state() == State.STARTING || state() == State.RUNNING;
    }
}
