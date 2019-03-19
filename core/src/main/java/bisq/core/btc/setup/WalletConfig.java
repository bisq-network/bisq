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

import bisq.core.app.BisqEnvironment;
import bisq.core.btc.nodes.ProxySocketFactory;
import bisq.core.btc.wallet.BisqRiskAnalysis;

import bisq.common.app.Version;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.WalletExtension;
import org.bitcoinj.wallet.WalletProtobufSerializer;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;

import java.nio.channels.FileLock;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

import java.util.List;
import java.util.concurrent.TimeUnit;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

// Derived from WalletAppKit
// Does the basic wiring
@Slf4j
public class WalletConfig extends AbstractIdleService {
    private static final int TIMEOUT = 120 * 1000;  // connectTimeoutMillis. 60 sec used in bitcoinj, but for Tor we allow more.

    ///////////////////////////////////////////////////////////////////////////////////////////
    // WalletFactory
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface BisqWalletFactory extends WalletProtobufSerializer.WalletFactory {
        Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup, boolean isBsqWallet);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Context context;
    private final NetworkParameters params;
    private final File directory;
    private final String btcWalletFileName;
    private final String bsqWalletFileName;
    private final String spvChainFileName;
    private final Socks5Proxy socks5Proxy;
    private final BisqWalletFactory walletFactory;
    private final BisqEnvironment bisqEnvironment;
    private final String userAgent;
    private int numConnectionForBtc;

    private volatile Wallet vBtcWallet;
    @Nullable
    private volatile Wallet vBsqWallet;
    private volatile File vBtcWalletFile;
    @Nullable
    private volatile File vBsqWalletFile;
    @Nullable
    private DeterministicSeed seed;

    private volatile BlockChain vChain;
    private volatile BlockStore vStore;
    private volatile PeerGroup vPeerGroup;
    private boolean useAutoSave = true;
    private PeerAddress[] peerAddresses;
    private PeerDataEventListener downloadListener;
    private boolean autoStop = true;
    private InputStream checkpoints;
    private boolean blockingStartup = true;
    @Getter
    @Setter
    private int minBroadcastConnections;

    @Nullable
    private PeerDiscovery discovery;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WalletConfig(NetworkParameters params,
                        Socks5Proxy socks5Proxy,
                        File directory,
                        BisqEnvironment bisqEnvironment,
                        String userAgent,
                        int numConnectionForBtc,
                        @SuppressWarnings("SameParameterValue") String btcWalletFileName,
                        @SuppressWarnings("SameParameterValue") String bsqWalletFileName,
                        @SuppressWarnings("SameParameterValue") String spvChainFileName) {
        this.bisqEnvironment = bisqEnvironment;
        this.userAgent = userAgent;
        this.numConnectionForBtc = numConnectionForBtc;
        this.context = new Context(params);
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.btcWalletFileName = checkNotNull(btcWalletFileName);
        this.bsqWalletFileName = bsqWalletFileName;
        this.spvChainFileName = spvChainFileName;
        this.socks5Proxy = socks5Proxy;

        walletFactory = new BisqWalletFactory() {
            @Override
            public Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup) {
                // This is called when we load an existing wallet
                // We have already the chain here so we can use this to distinguish.
                List<DeterministicKeyChain> deterministicKeyChains = keyChainGroup.getDeterministicKeyChains();
                if (!deterministicKeyChains.isEmpty() && deterministicKeyChains.get(0) instanceof BisqDeterministicKeyChain) {
                    return new BsqWallet(params, keyChainGroup);
                } else {
                    return new Wallet(params, keyChainGroup);
                }
            }

            @Override
            public Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup, boolean isBsqWallet) {
                // This is called at first startup when we create the wallet
                if (isBsqWallet) {
                    return new BsqWallet(params, keyChainGroup);
                } else {
                    return new Wallet(params, keyChainGroup);
                }
            }
        };

        String path = null;
        if (params.equals(MainNetParams.get())) {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            path = "/wallet/checkpoints.txt";
        } else if (params.equals(TestNet3Params.get())) {
            path = "/wallet/checkpoints.testnet.txt";
        }
        if (path != null) {
            try {
                setCheckpoints(getClass().getResourceAsStream(path));
            } catch (Exception e) {
                e.printStackTrace();
                log.error(e.toString());
            }
        }
    }

    private PeerGroup createPeerGroup() {
        PeerGroup peerGroup;
        // no proxy case.
        if (socks5Proxy == null) {
            peerGroup = new PeerGroup(params, vChain);
            // For dao testnet (server side regtest) we prevent to connect to a localhost node to avoid confusion
            // if local btc node is not synced with our dao testnet master node.
        } else {
            // proxy case (tor).
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(socks5Proxy.getInetAddress().getHostName(),
                            socks5Proxy.getPort()));

            ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
            // We don't use tor mode if we have a local node running
            BlockingClientManager blockingClientManager = bisqEnvironment.isBitcoinLocalhostNodeRunning() ?
                    new BlockingClientManager() :
                    new BlockingClientManager(proxySocketFactory);

            peerGroup = new PeerGroup(params, vChain, blockingClientManager);

            blockingClientManager.setConnectTimeoutMillis(TIMEOUT);
            peerGroup.setConnectTimeoutMillis(TIMEOUT);
        }

        // For dao testnet (server side regtest) we prevent to connect to a localhost node to avoid confusion
        // if local btc node is not synced with our dao testnet master node.
        if (BisqEnvironment.getBaseCurrencyNetwork().isDaoTestNet())
            peerGroup.setUseLocalhostPeerWhenPossible(false);

        return peerGroup;
    }

    /**
     * Will only connect to the given addresses. Cannot be called after startup.
     */
    public WalletConfig setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
        return this;
    }


    /**
     * If true, the wallet will save itself to disk automatically whenever it changes.
     */
    public WalletConfig setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    public WalletConfig setDownloadListener(PeerDataEventListener listener) {
        this.downloadListener = listener;
        return this;
    }

    /**
     * If true, will register a shutdown hook to stop the library. Defaults to true.
     */
    public WalletConfig setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated with BuildCheckpoints. It makes initial
     * block sync faster for new users - please refer to the documentation on the bitcoinj website for further details.
     */
    private void setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Utils.closeUnchecked(this.checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
    }

    /**
     * If true (the default) then the startup of this service won't be considered complete until the network has been
     * brought up, peer connections established and the block chain synchronised. Therefore startAndWait() can
     * potentially take a very long time. If false, then startup is considered complete once the network activity
     * begins and peer connections/block chain sync will continue in the background.
     */
    public WalletConfig setBlockingStartup(@SuppressWarnings("SameParameterValue") boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
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
    public WalletConfig setSeed(@Nullable DeterministicSeed seed) {
        this.seed = seed;
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
     * <p>Override this to return wallet extensions if any are necessary.</p>
     * <p>
     * <p>When this is called, chain(), store(), and peerGroup() will return the created objects, however they are not
     * initialized/started.</p>
     */
    private List<WalletExtension> provideWalletExtensions() {
        return ImmutableList.of();
    }

    /**
     * Override this to use a {@link BlockStore} that isn't the default of {@link SPVBlockStore}.
     */
    private BlockStore provideBlockStore(File file) throws BlockStoreException {
        return new ClearableSPVBlockStore(params, file);
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    void onSetupCompleted() {
    }

    /**
     * Tests to see if the spvchain file has an operating system file lock on it. Useful for checking if your app
     * is already running. If another copy of your app is running and you start the appkit anyway, an exception will
     * be thrown during the startup process. Returns false if the chain file does not exist or is a directory.
     */
    public boolean isChainFileLocked() throws IOException {
        RandomAccessFile file2 = null;
        try {
            File file = new File(directory, spvChainFileName);
            if (!file.exists())
                return false;
            if (file.isDirectory())
                return false;
            file2 = new RandomAccessFile(file, "rw");
            FileLock lock = file2.getChannel().tryLock();
            if (lock == null)
                return true;
            lock.release();
            return false;
        } finally {
            if (file2 != null)
                file2.close();
        }
    }

    @Override
    protected void startUp() throws Exception {
        // Runs in a separate thread.
        Context.propagate(context);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Could not create directory " + directory.getAbsolutePath());
            }
        }
        log.info("Wallet directory: {}", directory);
        try {
            File chainFile = new File(directory, spvChainFileName);
            boolean chainFileExists = chainFile.exists();

            // BTC wallet
            vBtcWalletFile = new File(directory, btcWalletFileName);
            boolean shouldReplayWallet = (vBtcWalletFile.exists() && !chainFileExists) || seed != null;
            BisqKeyChainGroup keyChainGroup;
            if (seed != null)
                keyChainGroup = new BisqKeyChainGroup(params, new BtcDeterministicKeyChain(seed), true);
            else
                keyChainGroup = new BisqKeyChainGroup(params, true);
            vBtcWallet = createOrLoadWallet(vBtcWalletFile, shouldReplayWallet, keyChainGroup, false, seed);

            vBtcWallet.allowSpendingUnconfirmedTransactions();
            vBtcWallet.setRiskAnalyzer(new BisqRiskAnalysis.Analyzer());

            if (seed != null)
                keyChainGroup = new BisqKeyChainGroup(params, new BisqDeterministicKeyChain(seed), false);
            else
                keyChainGroup = new BisqKeyChainGroup(params, new BisqDeterministicKeyChain(vBtcWallet.getKeyChainSeed()), false);

            // BSQ wallet
            vBsqWalletFile = new File(directory, bsqWalletFileName);
            vBsqWallet = createOrLoadWallet(vBsqWalletFile, shouldReplayWallet, keyChainGroup, true, seed);
            vBsqWallet.setRiskAnalyzer(new BisqRiskAnalysis.Analyzer());

            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = provideBlockStore(chainFile);
            if (!chainFileExists || seed != null) {
                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;

                    if (seed != null) {
                        // we created both wallets at the same time
                        time = seed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Clearing the chain file in preparation from restore.");
                            ((ClearableSPVBlockStore) vStore).clear();
                        }
                    } else {
                        time = vBtcWallet.getEarliestKeyCreationTime();
                    }


                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Clearing the chain file in preparation from restore.");
                    ((ClearableSPVBlockStore) vStore).clear();
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();

            if (minBroadcastConnections > 0)
                vPeerGroup.setMinBroadcastConnections(minBroadcastConnections);

            vPeerGroup.setUserAgent(userAgent, Version.VERSION);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                int maxConnections = Math.min(numConnectionForBtc, peerAddresses.length);
                log.info("We try to connect to {} btc nodes", maxConnections);
                vPeerGroup.setMaxConnections(maxConnections);
                peerAddresses = null;
            } else if (!params.equals(RegTestParams.get())) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vBtcWallet);
            vPeerGroup.addWallet(vBtcWallet);

            if (vBsqWallet != null) {
                //noinspection ConstantConditions
                vChain.addWallet(vBsqWallet);
                //noinspection ConstantConditions
                vPeerGroup.addWallet(vBsqWallet);
            }

            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();

                final DownloadProgressTracker listener = new DownloadProgressTracker();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        final PeerDataEventListener listener = downloadListener == null ?
                                new DownloadProgressTracker() : downloadListener;
                        vPeerGroup.startBlockChainDownload(listener);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        throw new RuntimeException(t);

                    }
                });
            }
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    void setPeerNodesForLocalHost() {
        try {
            setPeerNodes(new PeerAddress(InetAddress.getLocalHost(), params.getPort()));
        } catch (UnknownHostException e) {
            log.error(e.toString());
            e.printStackTrace();
            // Borked machine with no loopback adapter configured properly.
            throw new RuntimeException(e);
        }
    }

    private Wallet createOrLoadWallet(File walletFile, boolean shouldReplayWallet,
                                      BisqKeyChainGroup keyChainGroup, boolean isBsqWallet, DeterministicSeed restoreFromSeed)
            throws Exception {

        if (restoreFromSeed != null)
            maybeMoveOldWalletOutOfTheWay(walletFile);

        Wallet wallet;
        if (walletFile.exists()) {
            wallet = loadWallet(walletFile, shouldReplayWallet, keyChainGroup.isUseBitcoinDeterministicKeyChain());
        } else {
            wallet = createWallet(keyChainGroup, isBsqWallet);
            wallet.freshReceiveKey();
            wallet.saveToFile(walletFile);
        }

        if (useAutoSave) wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

        return wallet;
    }

    private void maybeMoveOldWalletOutOfTheWay(File vWalletFile) {
        if (!vWalletFile.exists()) return;

        int counter = 1;
        File newName;
        do {
            newName = new File(vWalletFile.getParent(), "Backup " + counter + " for " + vWalletFile.getName());
            counter++;
        } while (newName.exists());

        log.info("Renaming old wallet file {} to {}", vWalletFile, newName);
        if (!vWalletFile.renameTo(newName)) {
            // This should not happen unless something is really messed up.
            throw new RuntimeException("Failed to rename wallet for restore");
        }
    }

    private Wallet loadWallet(File walletFile, boolean shouldReplayWallet, boolean useBitcoinDeterministicKeyChain) throws Exception {
        Wallet wallet;
        try (FileInputStream walletStream = new FileInputStream(walletFile)) {
            List<WalletExtension> extensions = provideWalletExtensions();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            if (walletFactory != null)
                serializer = new WalletProtobufSerializer(walletFactory);
            else
                serializer = new WalletProtobufSerializer();

            serializer.setKeyChainFactory(new BisqKeyChainFactory(useBitcoinDeterministicKeyChain));
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        }
        return wallet;
    }

    private Wallet createWallet(BisqKeyChainGroup keyChainGroup, boolean isBsqWallet) {
        checkNotNull(walletFactory, "walletFactory must not be null");
        return walletFactory.create(params, keyChainGroup, isBsqWallet);
    }

    private void installShutdownHook() {
        if (autoStop) Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Thread.currentThread().setName("ShutdownHook");
            try {
                WalletConfig.this.stopAsync();
                WalletConfig.this.awaitTerminated();
            } catch (Throwable ignore) {
            }
        }));
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);
            vPeerGroup.stop();
            vBtcWallet.saveToFile(vBtcWalletFile);
            if (vBsqWallet != null && vBsqWalletFile != null)
                //noinspection ConstantConditions,ConstantConditions
                vBsqWallet.saveToFile(vBsqWalletFile);
            vStore.close();

            vPeerGroup = null;
            vBtcWallet = null;
            vBsqWallet = null;
            vStore = null;
            vChain = null;
        } catch (BlockStoreException e) {
            throw new IOException(e);
        } catch (Throwable ignore) {
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

    public Wallet getBtcWallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vBtcWallet;
    }

    @Nullable
    public Wallet getBsqWallet() {
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
}
