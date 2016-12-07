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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.AbstractIdleService;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.subgraph.orchid.TorClient;
import org.bitcoinj.core.*;
import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.SPVBlockStore;
import org.bitcoinj.store.WalletProtobufSerializer;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.KeyChainGroup;
import org.bitcoinj.wallet.Protos;
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
import java.util.concurrent.TimeoutException;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public class BitSquareWalletAppKit extends AbstractIdleService {
    private static final Logger log = LoggerFactory.getLogger(BitSquareWalletAppKit.class);

    protected final String walletFilePrefix;
    protected final String tokenWalletFilePrefix;
    protected volatile Wallet vWallet;
    protected volatile Wallet vTokenWallet;
    protected volatile File vWalletFile;
    protected volatile File vTokenWalletFile;
    @Nullable
    protected DeterministicSeed restoreWalletFromSeed;
    @Nullable
    protected DeterministicSeed restoreTokenWalletFromSeed;

    protected final NetworkParameters params;
    protected volatile BlockChain vChain;
    protected volatile BlockStore vStore;
    protected volatile PeerGroup vPeerGroup;
    protected final File directory;
    protected boolean useAutoSave = true;
    protected PeerAddress[] peerAddresses;
    protected PeerEventListener downloadListener;
    protected boolean autoStop = true;
    protected InputStream checkpoints;
    protected boolean blockingStartup = true;
    protected boolean useTor = false;   // Perhaps in future we can change this to true.
    protected String userAgent, version;
    protected WalletProtobufSerializer.WalletFactory walletFactory;

    @Nullable
    protected PeerDiscovery discovery;

    protected volatile Context context;
    private long bloomFilterTweak = 0;
    private double bloomFilterFPRate = -1;
    private int lookaheadSize = -1;

    private Socks5Proxy socks5Proxy;

    /**
     * Creates a new WalletAppKitBitSquare, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public BitSquareWalletAppKit(NetworkParameters params, Socks5Proxy socks5Proxy, File directory, String walletFilePrefix, String tokenWalletFilePrefix) {
        this(new Context(params), directory, walletFilePrefix, tokenWalletFilePrefix);
        this.socks5Proxy = socks5Proxy;
    }

    /**
     * Creates a new WalletAppKitBitSquare, with a newly created {@link Context}. Files will be stored in the given directory.
     */
    public BitSquareWalletAppKit(NetworkParameters params, File directory, String walletFilePrefix, String tokenWalletFilePrefix) {
        this(new Context(params), directory, walletFilePrefix, tokenWalletFilePrefix);
    }

    /**
     * Creates a new WalletAppKitBitSquare, with the given {@link Context}. Files will be stored in the given directory.
     */
    public BitSquareWalletAppKit(Context context, File directory, String walletFilePrefix, String tokenWalletFilePrefix) {
        this.context = context;
        this.params = checkNotNull(context.getParams());
        this.directory = checkNotNull(directory);
        this.walletFilePrefix = checkNotNull(walletFilePrefix);
        this.tokenWalletFilePrefix = tokenWalletFilePrefix;
        if (!Utils.isAndroidRuntime()) {
            InputStream stream = BitSquareWalletAppKit.class.getResourceAsStream("/" + params.getId() + ".checkpoints");
            if (stream != null)
                setCheckpoints(stream);
        }
    }

    public Socks5Proxy getProxy() {
        return socks5Proxy;
    }

    protected PeerGroup createPeerGroup() throws TimeoutException {
        // no proxy case.
        if (socks5Proxy == null) {
            if (useTor) {
                TorClient torClient = new TorClient();
                torClient.getConfig().setDataDirectory(directory);
                return PeerGroup.newWithTor(params, vChain, torClient);
            } else
                return new PeerGroup(params, vChain);
        }

        // proxy case.
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


    /**
     * Will only connect to the given addresses. Cannot be called after startup.
     */
    public BitSquareWalletAppKit setPeerNodes(PeerAddress... addresses) {
        checkState(state() == State.NEW, "Cannot call after startup");
        this.peerAddresses = addresses;
        return this;
    }

    /**
     * Will only connect to localhost. Cannot be called after startup.
     */
    public BitSquareWalletAppKit connectToLocalHost() {
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
    public BitSquareWalletAppKit setAutoSave(boolean value) {
        checkState(state() == State.NEW, "Cannot call after startup");
        useAutoSave = value;
        return this;
    }

    /**
     * If you want to learn about the sync process, you can provide a listener here. For instance, a
     * {@link org.bitcoinj.core.DownloadProgressTracker} is a good choice. This has no effect unless setBlockingStartup(false) has been called
     * too, due to some missing implementation code.
     */
    public BitSquareWalletAppKit setDownloadListener(PeerEventListener listener) {
        this.downloadListener = listener;
        return this;
    }

    /**
     * If true, will register a shutdown hook to stop the library. Defaults to true.
     */
    public BitSquareWalletAppKit setAutoStop(boolean autoStop) {
        this.autoStop = autoStop;
        return this;
    }

    /**
     * If set, the file is expected to contain a checkpoints file calculated with BuildCheckpoints. It makes initial
     * block sync faster for new users - please refer to the documentation on the bitcoinj website for further details.
     */
    public BitSquareWalletAppKit setCheckpoints(InputStream checkpoints) {
        if (this.checkpoints != null)
            Utils.closeUnchecked(this.checkpoints);
        this.checkpoints = checkNotNull(checkpoints);
        return this;
    }

    /**
     * If true (the default) then the startup of this service won't be considered complete until the network has been
     * brought up, peer connections established and the block chain synchronised. Therefore {@link #startAndWait()} can
     * potentially take a very long time. If false, then startup is considered complete once the network activity
     * begins and peer connections/block chain sync will continue in the background.
     */
    public BitSquareWalletAppKit setBlockingStartup(boolean blockingStartup) {
        this.blockingStartup = blockingStartup;
        return this;
    }

    /**
     * Sets the string that will appear in the subver field of the version message.
     *
     * @param userAgent A short string that should be the name of your app, e.g. "My Wallet"
     * @param version   A short string that contains the version number, e.g. "1.0-BETA"
     */
    public BitSquareWalletAppKit setUserAgent(String userAgent, String version) {
        this.userAgent = checkNotNull(userAgent);
        this.version = checkNotNull(version);
        return this;
    }

    /**
     * If called, then an embedded Tor client library will be used to connect to the P2P network. The user does not need
     * any additional software for this: it's all pure Java. As of April 2014 <b>this mode is experimental</b>.
     */
    public BitSquareWalletAppKit useTor() {
        this.useTor = true;
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
    public BitSquareWalletAppKit restoreWalletFromSeed(DeterministicSeed seed) {
        this.restoreWalletFromSeed = seed;
        return this;
    }

    public BitSquareWalletAppKit restoreTokenWalletFromSeed(DeterministicSeed seed) {
        this.restoreTokenWalletFromSeed = seed;
        return this;
    }

    /**
     * Sets the peer discovery class to use. If none is provided then DNS is used, which is a reasonable default.
     */
    public BitSquareWalletAppKit setDiscovery(@Nullable PeerDiscovery discovery) {
        this.discovery = discovery;
        return this;
    }

    public BitSquareWalletAppKit setBloomFilterFalsePositiveRate(double bloomFilterFPRate) {
        this.bloomFilterFPRate = bloomFilterFPRate;
        return this;
    }

    public BitSquareWalletAppKit setBloomFilterTweak(long bloomFilterTweak) {
        this.bloomFilterTweak = bloomFilterTweak;
        return this;
    }

    public BitSquareWalletAppKit setLookaheadSize(int lookaheadSize) {
        this.lookaheadSize = lookaheadSize;
        return this;
    }

    /**
     * <p>Override this to return wallet extensions if any are necessary.</p>
     * <p>
     * <p>When this is called, chain(), store(), and peerGroup() will return the created objects, however they are not
     * initialized/started.</p>
     */
    protected List<WalletExtension> provideWalletExtensions() throws Exception {
        return ImmutableList.of();
    }

    /**
     * Override this to use a {@link BlockStore} that isn't the default of {@link SPVBlockStore}.
     */
    protected BlockStore provideBlockStore(File file) throws BlockStoreException {
        return new SPVBlockStore(params, file);
    }

    /**
     * This method is invoked on a background thread after all objects are initialised, but before the peer group
     * or block chain download is started. You can tweak the objects configuration here.
     */
    protected void onSetupCompleted() {
    }

    /**
     * Tests to see if the spvchain file has an operating system file lock on it. Useful for checking if your app
     * is already running. If another copy of your app is running and you start the appkit anyway, an exception will
     * be thrown during the startup process. Returns false if the chain file does not exist or is a directory.
     */
    public boolean isChainFileLocked() throws IOException {
        RandomAccessFile file2 = null;
        try {
            File file = new File(directory, walletFilePrefix + ".spvchain");
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
            File chainFile = new File(directory, walletFilePrefix + ".spvchain");
            boolean chainFileExists = chainFile.exists();
            vWalletFile = new File(directory, walletFilePrefix + ".wallet");
            boolean shouldReplayWallet = (vWalletFile.exists() && !chainFileExists) || restoreWalletFromSeed != null;
            vWallet = createOrLoadWallet(vWalletFile, shouldReplayWallet, restoreWalletFromSeed);

            vTokenWalletFile = new File(directory, tokenWalletFilePrefix + ".wallet");
            boolean shouldReplayTokenWallet = (vTokenWalletFile.exists() && !chainFileExists) || restoreTokenWalletFromSeed != null;
            vTokenWallet = createOrLoadWallet(vTokenWalletFile, shouldReplayTokenWallet, restoreTokenWalletFromSeed);

            // Initiate Bitcoin network objects (block store, blockchain and peer group)
            vStore = provideBlockStore(chainFile);
            if (!chainFileExists || restoreWalletFromSeed != null || restoreTokenWalletFromSeed != null) {
                if (checkpoints != null) {
                    // Initialize the chain file with a checkpoint to speed up first-run sync.
                    long time;

                    if (restoreWalletFromSeed != null || restoreTokenWalletFromSeed != null) {
                        // we created both wallets at the same time
                        time = restoreWalletFromSeed.getCreationTimeSeconds();
                        if (chainFileExists) {
                            log.info("Deleting the chain file in preparation from restore.");
                            vStore.close();
                            if (!chainFile.delete())
                                throw new IOException("Failed to delete chain file in preparation for restore.");
                            vStore = new SPVBlockStore(params, chainFile);
                        }
                    } else {
                        time = vWallet.getEarliestKeyCreationTime();
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
            } else if (params != RegTestParams.get() && !useTor) {
                vPeerGroup.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
            }
            vChain.addWallet(vWallet);
            vChain.addWallet(vTokenWallet);
            vPeerGroup.addWallet(vWallet);
            vPeerGroup.addWallet(vTokenWallet);
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
                    public void onFailure(Throwable t) {
                        throw new RuntimeException(t);

                    }
                });
            }
        } catch (BlockStoreException e) {
            throw new IOException(e);
        }
    }

    private Wallet createOrLoadWallet(File walletFile, boolean shouldReplayWallet, DeterministicSeed restoreFromSeed) throws Exception {
        Wallet wallet;

        maybeMoveOldWalletOutOfTheWay(walletFile, restoreFromSeed);

        if (walletFile.exists()) {
            wallet = loadWallet(walletFile, shouldReplayWallet);
        } else {
            wallet = createWallet(restoreFromSeed);
            wallet.freshReceiveKey();
            for (WalletExtension e : provideWalletExtensions()) {
                wallet.addExtension(e);
            }

            // Currently the only way we can be sure that an extension is aware of its containing wallet is by
            // deserializing the extension (see WalletExtension#deserializeWalletExtension(Wallet, byte[]))
            // Hence, we first save and then load wallet to ensure any extensions are correctly initialized.
            wallet.saveToFile(walletFile);
            wallet = loadWallet(walletFile, false);
        }

        if (useAutoSave) wallet.autosaveToFile(walletFile, 5, TimeUnit.SECONDS, null);

        return wallet;
    }

    private Wallet loadWallet(File walletFile, boolean shouldReplayWallet) throws Exception {
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
            wallet = serializer.readWallet(params, extArray, proto);
            if (shouldReplayWallet)
                wallet.reset();
        } finally {
            walletStream.close();
        }
        return wallet;
    }

    protected Wallet createWallet(DeterministicSeed restoreFromSeed) {
        KeyChainGroup kcg;
        if (restoreFromSeed != null)
            kcg = new KeyChainGroup(params, restoreFromSeed);
        else
            kcg = new KeyChainGroup(params);

        if (lookaheadSize != -1)
            kcg.setLookaheadSize(lookaheadSize);

        if (walletFactory != null) {
            return walletFactory.create(params, kcg);
        } else {
            return new Wallet(params, kcg);  // default
        }
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
                    BitSquareWalletAppKit.this.stopAsync();
                    BitSquareWalletAppKit.this.awaitTerminated();
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
            vWallet.saveToFile(vWalletFile);
            vTokenWallet.saveToFile(vTokenWalletFile);
            vStore.close();

            vPeerGroup = null;
            vWallet = null;
            vTokenWallet = null;
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

    public Wallet wallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vWallet;
    }

    public Wallet tokenWallet() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vTokenWallet;
    }

    public PeerGroup peerGroup() {
        checkState(state() == State.STARTING || state() == State.RUNNING, "Cannot call until startup is complete");
        return vPeerGroup;
    }

    public File directory() {
        return directory;
    }
}
