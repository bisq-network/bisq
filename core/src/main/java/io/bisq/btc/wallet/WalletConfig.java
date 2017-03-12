/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.btc.wallet;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.btc.ProxySocketFactory;
import org.bitcoinj.core.*;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.store.WalletProtobufSerializer;
import org.bitcoinj.wallet.DeterministicKeyChain;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.*;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.UnknownHostException;
import java.nio.channels.FileLock;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

// Derived from WalletAppKit
// Does the basic wiring
public class WalletConfig extends AbstractIdleService {
    private static final Logger log = LoggerFactory.getLogger(WalletConfig.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // WalletFactory
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface BitsquareWalletFactory extends WalletProtobufSerializer.WalletFactory {
        Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup);

        Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup, boolean isBsqWallet);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    private final Context context;
    private final NetworkParameters params;
    private final File directory;
    private final String btcWalletFilePrefix;
    private final String bsqWalletFilePrefix;
    private final Socks5Proxy socks5Proxy;
    private final BitsquareWalletFactory walletFactory;

    private volatile Wallet vBtcWallet;
    private volatile Wallet vBsqWallet;
    private volatile File vBtcWalletFile;
    private volatile File vBsqWalletFile;
    @Nullable
    private DeterministicSeed seed;
    private int btcWalletLookaheadSize = -1;
    private int bsqWalletLookaheadSize = -1;

    private volatile BlockChain vChain;
    private volatile BlockStore vStore;
    private volatile PeerGroup vPeerGroup;
    private boolean useAutoSave = true;
    private PeerAddress[] peerAddresses;
    private PeerEventListener downloadListener;
    private boolean autoStop = true;
    private InputStream checkpoints;
    private boolean blockingStartup = true;

    private String userAgent;
    private String version;
    @Nullable
    private PeerDiscovery discovery;
    private long bloomFilterTweak = 0;
    private double bloomFilterFPRate = -1;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public WalletConfig(NetworkParameters params, Socks5Proxy socks5Proxy,
                        File directory, String btcWalletFilePrefix,
                        String bsqWalletFilePrefix) {
        this.context = new Context(params);
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.btcWalletFilePrefix = checkNotNull(btcWalletFilePrefix);
        this.bsqWalletFilePrefix = bsqWalletFilePrefix;
        this.socks5Proxy = socks5Proxy;

        walletFactory = new BitsquareWalletFactory() {
            @Override
            public Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup) {
                // This is called when we load an existing wallet
                // We have already the chain here so we can use this to distinguish.
                //TODO refactor, make it more explicit
                List<DeterministicKeyChain> deterministicKeyChains = keyChainGroup.getDeterministicKeyChains();
                if (!deterministicKeyChains.isEmpty() && deterministicKeyChains.get(0) instanceof BsqDeterministicKeyChain)
                    return new BsqWallet(params, keyChainGroup);
                else
                    return new Wallet(params, keyChainGroup);
            }

            @Override
            public Wallet create(NetworkParameters params, KeyChainGroup keyChainGroup, boolean isBsqWallet) {
                // This is called at first startup when we create the wallet
                if (isBsqWallet)
                    return new BsqWallet(params, keyChainGroup);
                else
                    return new Wallet(params, keyChainGroup);
            }
        };

        String path = null;
        if (params.equals(MainNetParams.get())) {
            // Checkpoints are block headers that ship inside our app: for a new user, we pick the last header
            // in the checkpoints file and then download the rest from the network. It makes things much faster.
            // Checkpoint files are made using the BuildCheckpoints tool and usually we have to download the
            // last months worth or more (takes a few seconds).
            path = "/wallet/checkpoints";
        } else if (params.equals(TestNet3Params.get())) {
            path = "/wallet/checkpoints.testnet";
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
        // no proxy case.
        if (socks5Proxy == null) {
            return new PeerGroup(params, vChain);
        } else {
            // proxy case (tor).
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(socks5Proxy.getInetAddress().getHostName(),
                            socks5Proxy.getPort()));

            int CONNECT_TIMEOUT_MSEC = 60 * 1000;  // same value used in bitcoinj.
            ProxySocketFactory proxySocketFactory = new ProxySocketFactory(proxy);
            BlockingClientManager mgr = new BlockingClientManager(proxySocketFactory);
            PeerGroup peerGroup = new PeerGroup(params, vChain, mgr);

            mgr.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);
            peerGroup.setConnectTimeoutMillis(CONNECT_TIMEOUT_MSEC);

            return peerGroup;
        }
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
     * Will only connect to localhost. Cannot be called after startup.
     */
    public WalletConfig connectToLocalHost() {
        try {
            final InetAddress localHost = InetAddress.getLocalHost();
            return setPeerNodes(new PeerAddress(localHost, params.getPort()));
        } catch (UnknownHostException e) {
            // Borked machine with no loopback adapter configured properly.
            throw new RuntimeException(e);
        }
    }

    /**
     * If true, the wallet will save itself to disk automatically whenever it changes.
     */
    public WalletConfig setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    /**
     * If you want to learn about the sync process, you can provide a listener here. For instance, a
     * {@link org.bitcoinj.core.DownloadProgressTracker} is a good choice. This has no effect unless setBlockingStartup(false) has been called
     * too, due to some missing implementation code.
     */
    public WalletConfig setDownloadListener(PeerEventListener listener) {
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
    public WalletConfig setBlockingStartup(boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version message.
     *
     * @param userAgent A short string that should be the name of your app, e.g. "My Wallet"
     * @param version   A short string that contains the version number, e.g. "1.0-BETA"
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

    public WalletConfig setBloomFilterFalsePositiveRate(double bloomFilterFPRate) {
        this.bloomFilterFPRate = bloomFilterFPRate;
        return this;
    }

    public WalletConfig setBloomFilterTweak(long bloomFilterTweak) {
        this.bloomFilterTweak = bloomFilterTweak;
        return this;
    }

    public WalletConfig setBtcWalletLookaheadSize(int lookaheadSize) {
        this.btcWalletLookaheadSize = lookaheadSize;
        return this;
    }

    public WalletConfig setBsqWalletLookaheadSize(int lookaheadSize) {
        this.bsqWalletLookaheadSize = lookaheadSize;
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
        return new SPVBlockStore(params, file);
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    void onSetupCompleted() {
        vBtcWallet.allowSpendingUnconfirmedTransactions();

        // TODO do we want that here?
        vBsqWallet.allowSpendingUnconfirmedTransactions();

        if (params != RegTestParams.get())
            peerGroup().setMaxConnections(11);
    }

    /**
     * Tests to see if the spvchain file has an operating system file lock on it. Useful for checking if your app
     * is already running. If another copy of your app is running and you start the appkit anyway, an exception will
     * be thrown during the startup process. Returns false if the chain file does not exist or is a directory.
     */
    public boolean isChainFileLocked() throws IOException {
        RandomAccessFile file2 = null;
        try {
            File file = new File(directory, btcWalletFilePrefix + ".spvchain");
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
        log.info("Starting up with directory = {}", directory);
        try {
            File chainFile = new File(directory, btcWalletFilePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();

            // BTC wallet
            vBtcWalletFile = new File(directory, btcWalletFilePrefix + ".wallet");
            boolean shouldReplayWallet = (vBtcWalletFile.exists() && !chainFileExists) || seed != null;
            BitsquareKeyChainGroup keyChainGroup;
            if (seed != null)
                keyChainGroup = new BitsquareKeyChainGroup(params, new BtcDeterministicKeyChain(seed), true, btcWalletLookaheadSize);
            else
                keyChainGroup = new BitsquareKeyChainGroup(params, true, btcWalletLookaheadSize);
            vBtcWallet = createOrLoadWallet(vBtcWalletFile, shouldReplayWallet, seed, keyChainGroup, false);

            // BSQ walelt
            vBsqWalletFile = new File(directory, bsqWalletFilePrefix + ".wallet");
            if (seed != null)
                keyChainGroup = new BitsquareKeyChainGroup(params, new BsqDeterministicKeyChain(seed), false, bsqWalletLookaheadSize);
            else
                keyChainGroup = new BitsquareKeyChainGroup(params, new BsqDeterministicKeyChain(vBtcWallet.getKeyChainSeed()), false, bsqWalletLookaheadSize);
            vBsqWallet = createOrLoadWallet(vBsqWalletFile, shouldReplayWallet, seed, keyChainGroup, true);

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
                            log.info("Deleting the chain file in preparation from restore.");
                            vStore.close();
                            if (!chainFile.delete())
                                throw new IOException("Failed to delete chain file in preparation for restore.");
                            vStore = new SPVBlockStore(params, chainFile);
                        }
                    } else {
                        time = vBtcWallet.getEarliestKeyCreationTime();
                    }


                    if (time > 0)
                        CheckpointManager.checkpoint(params, checkpoints, vStore, time);
                    else
                        log.warn("Creating a new uncheckpointed block store due to a wallet with a creation time of zero: this will result in a very slow chain sync");
                } else if (chainFileExists) {
                    log.info("Deleting the chain file in preparation from restore.");
                    vStore.close();
                    if (!chainFile.delete())
                        throw new IOException("Failed to delete chain file in preparation for restore.");
                    vStore = new SPVBlockStore(params, chainFile);
                }
            }
            vChain = new BlockChain(params, vStore);
            vPeerGroup = createPeerGroup();

            if (bloomFilterFPRate != -1)
                vPeerGroup.setBloomFilterFalsePositiveRate(bloomFilterFPRate);

            if (bloomFilterTweak != 0)
                vPeerGroup.setBloomFilterTweak(bloomFilterTweak);

            if (this.userAgent != null)
                vPeerGroup.setUserAgent(userAgent, version);

            // Set up peer addresses or discovery first, so if wallet extensions try to broadcast a transaction
            // before we're actually connected the broadcast waits for an appropriate number of connections.
            if (peerAddresses != null) {
                for (PeerAddress addr : peerAddresses) vPeerGroup.addAddress(addr);
                vPeerGroup.setMaxConnections(peerAddresses.length);
                peerAddresses = null;
            } else if (params.equals(RegTestParams.get())) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vBtcWallet);
            vChain.addWallet(vBsqWallet);
            vPeerGroup.addWallet(vBtcWallet);
            vPeerGroup.addWallet(vBsqWallet);
            onSetupCompleted();

            if (blockingStartup) {
                vPeerGroup.start();
                // Make sure we shut down cleanly.
                installShutdownHook();

                // TODO: Be able to use the provided download listener when doing a blocking startup.
                final DownloadProgressTracker listener = new DownloadProgressTracker();
                vPeerGroup.startBlockChainDownload(listener);
                listener.await();
            } else {
                Futures.addCallback(vPeerGroup.startAsync(), new FutureCallback() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        final PeerEventListener l = downloadListener == null ? new DownloadProgressTracker() : downloadListener;
                        vPeerGroup.startBlockChainDownload(l);
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

    private Wallet createOrLoadWallet(File walletFile, boolean shouldReplayWallet, @Nullable DeterministicSeed seed,
                                      BitsquareKeyChainGroup keyChainGroup, boolean isBsqWallet)
            throws Exception {
        Wallet wallet;

        if (seed != null)
            maybeMoveOldWalletOutOfTheWay(walletFile, seed);

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

    private Wallet loadWallet(File walletFile, boolean shouldReplayWallet, boolean useBitcoinDeterministicKeyChain) throws Exception {
        Wallet wallet;
        FileInputStream walletStream = new FileInputStream(walletFile);
        try {
            List<WalletExtension> extensions = provideWalletExtensions();
            WalletExtension[] extArray = extensions.toArray(new WalletExtension[extensions.size()]);
            Protos.Wallet proto = WalletProtobufSerializer.parseToProto(walletStream);
            final WalletProtobufSerializer serializer;
            if (walletFactory != null)
                serializer = new WalletProtobufSerializer(walletFactory);
            else
                serializer = new WalletProtobufSerializer();

            serializer.setKeyChainFactory(new BitsquareKeyChainFactory(useBitcoinDeterministicKeyChain));
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    private Wallet createWallet(BitsquareKeyChainGroup keyChainGroup, boolean isBsqWallet) {
        checkNotNull(walletFactory, "walletFactory must not be null");
        return walletFactory.create(params, keyChainGroup, isBsqWallet);
    }

    private void maybeMoveOldWalletOutOfTheWay(File walletFile, DeterministicSeed restoreFromSeed) {
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


    private void installShutdownHook() {
        if (autoStop) Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                try {
                    WalletConfig.this.stopAsync();
                    WalletConfig.this.awaitTerminated();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }

    @Override
    protected void shutDown() throws Exception {
        // Runs in a separate thread.
        try {
            Context.propagate(context);
            vPeerGroup.stop();
            vBtcWallet.saveToFile(vBtcWalletFile);
            vBsqWallet.saveToFile(vBsqWalletFile);
            vStore.close();

            vPeerGroup = null;
            vBtcWallet = null;
            vBsqWallet = null;
            vStore = null;
            vChain = null;
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

    public Wallet getBtcWallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vBtcWallet;
    }

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
