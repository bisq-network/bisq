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

package io.bisq.core.btc.wallet;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Service;
import com.google.inject.Inject;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.handlers.ExceptionHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.storage.FileUtil;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.btc.*;
import io.bisq.core.user.Preferences;
import io.bisq.network.DnsLookupTor;
import io.bisq.network.Socks5MultiDiscovery;
import io.bisq.network.Socks5ProxyProvider;
import javafx.beans.property.*;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.net.OnionCat;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

// Setup wallets and use WalletConfig for BitcoinJ wiring.
// Other like WalletConfig we are here always on the user thread. That is one reason why we do not
// merge WalletsSetup with WalletConfig to one class.
public class WalletsSetup {
    private static final Logger log = LoggerFactory.getLogger(WalletsSetup.class);

    private static final long STARTUP_TIMEOUT_SEC = 60;
    private final String btcWalletFileName;
    private static final String BSQ_WALLET_FILE_NAME = "bisq_BSQ.wallet";
    private static final String SPV_CHAIN_FILE_NAME = "bisq.spvchain";

    private final RegTestHost regTestHost;
    private final AddressEntryList addressEntryList;
    private final Preferences preferences;
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final BisqEnvironment bisqEnvironment;
    private final BitcoinNodes bitcoinNodes;
    private final NetworkParameters params;
    private final File walletDir;
    private final int socks5DiscoverMode;
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final ObjectProperty<List<Peer>> connectedPeers = new SimpleObjectProperty<>();
    private final DownloadListener downloadListener = new DownloadListener();
    private final List<Runnable> setupCompletedHandlers = new ArrayList<>();
    public final BooleanProperty shutDownComplete = new SimpleBooleanProperty();
    private WalletConfig walletConfig;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public WalletsSetup(RegTestHost regTestHost,
                        AddressEntryList addressEntryList,
                        Preferences preferences,
                        Socks5ProxyProvider socks5ProxyProvider,
                        BisqEnvironment bisqEnvironment,
                        BitcoinNodes bitcoinNodes,
                        @Named(BtcOptionKeys.WALLET_DIR) File appDir,
                        @Named(BtcOptionKeys.SOCKS5_DISCOVER_MODE) String socks5DiscoverModeString) {
        this.regTestHost = regTestHost;
        this.addressEntryList = addressEntryList;
        this.preferences = preferences;
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.bisqEnvironment = bisqEnvironment;
        this.bitcoinNodes = bitcoinNodes;

        this.socks5DiscoverMode = evaluateMode(socks5DiscoverModeString);

        btcWalletFileName = "bisq_" + BisqEnvironment.getBaseCurrencyNetwork().getCurrencyCode() + ".wallet";
        params = BisqEnvironment.getParameters();
        walletDir = new File(appDir, "wallet");
        PeerGroup.setIgnoreHttpSeeds(true);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

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
        log.info("Socks5Proxy for bitcoinj: socks5Proxy=" + socks5Proxy);

        walletConfig = new WalletConfig(params, socks5Proxy, walletDir, bisqEnvironment, btcWalletFileName,
                BSQ_WALLET_FILE_NAME, SPV_CHAIN_FILE_NAME) {
            @Override
            protected void onSetupCompleted() {
                //We are here in the btcj thread Thread[ STARTING,5,main]
                super.onSetupCompleted();

                final PeerGroup peerGroup = walletConfig.peerGroup();

                // We don't want to get our node white list polluted with nodes from AddressMessage calls.
                if (preferences.getBitcoinNodes() != null && !preferences.getBitcoinNodes().isEmpty())
                    peerGroup.setAddPeersFromAddressMessage(false);

                peerGroup.addConnectedEventListener((peer, peerCount) -> {
                    // We get called here on our user thread
                    numPeers.set(peerCount);
                    connectedPeers.set(peerGroup.getConnectedPeers());
                });
                peerGroup.addDisconnectedEventListener((peer, peerCount) -> {
                    // We get called here on our user thread
                    numPeers.set(peerCount);
                    connectedPeers.set(peerGroup.getConnectedPeers());
                });

                // Map to user thread
                UserThread.execute(() -> {
                    addressEntryList.onWalletReady(walletConfig.getBtcWallet());
                    timeoutTimer.stop();
                    setupCompletedHandlers.stream().forEach(Runnable::run);
                });

                // onSetupCompleted in walletAppKit is not the called on the last invocations, so we add a bit of delay
                UserThread.runAfter(resultHandler::handleResult, 100, TimeUnit.MILLISECONDS);
            }
        };

        configPeerNodes(socks5Proxy);
        walletConfig.setDownloadListener(downloadListener)
                .setBlockingStartup(false);

        // If seed is non-null it means we are restoring from backup.
        walletConfig.setSeed(seed);

        walletConfig.addListener(new Service.Listener() {
            @Override
            public void failed(@NotNull Service.State from, @NotNull Throwable failure) {
                walletConfig = null;
                log.error("Service failure from state: {}; failure={}", from, failure);
                timeoutTimer.stop();
                UserThread.execute(() -> exceptionHandler.handleException(failure));
            }
        }, Threading.USER_THREAD);

        walletConfig.startAsync();
    }

    public void shutDown() {
        if (walletConfig != null) {
            try {
                walletConfig.stopAsync();
                walletConfig.awaitTerminated(5, TimeUnit.SECONDS);
            } catch (Throwable ignore) {
            }
            shutDownComplete.set(true);
        } else {
            shutDownComplete.set(true);
        }
    }

    public boolean reSyncSPVChain() {
        try {
            return new File(walletDir, SPV_CHAIN_FILE_NAME).delete();
        } catch (Throwable t) {
            log.error(t.toString());
            t.printStackTrace();
            return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Initialize methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @VisibleForTesting
    private int evaluateMode(String socks5DiscoverModeString) {
        String[] socks5DiscoverModes = StringUtils.deleteWhitespace(socks5DiscoverModeString).split(",");
        int mode = 0;
        for (String socks5DiscoverMode : socks5DiscoverModes) {
            switch (socks5DiscoverMode) {
                case "ADDR":
                    mode |= Socks5MultiDiscovery.SOCKS5_DISCOVER_ADDR;
                    break;
                case "DNS":
                    mode |= Socks5MultiDiscovery.SOCKS5_DISCOVER_DNS;
                    break;
                case "ONION":
                    mode |= Socks5MultiDiscovery.SOCKS5_DISCOVER_ONION;
                    break;
                case "ALL":
                default:
                    mode |= Socks5MultiDiscovery.SOCKS5_DISCOVER_ALL;
                    break;
            }
        }
        return mode;
    }

    private void configPeerNodes(Socks5Proxy socks5Proxy) {
        if (params == RegTestParams.get()) {
            if (regTestHost == RegTestHost.REG_TEST_SERVER) {
                try {
                    walletConfig.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
                } catch (UnknownHostException e) {
                    log.error(e.toString());
                    e.printStackTrace();
                    throw new RuntimeException(e);
                }
            } else if (regTestHost == RegTestHost.LOCALHOST) {
                walletConfig.connectToLocalHost();
            }
        } else {
            List<BitcoinNodes.BtcNode> btcNodeList = new ArrayList<>();
            switch (BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()]) {
                case CUSTOM:
                    String bitcoinNodesString = preferences.getBitcoinNodes();
                    if (bitcoinNodesString != null) {
                        btcNodeList = Splitter.on(",")
                                .splitToList(StringUtils.deleteWhitespace(bitcoinNodesString))
                                .stream()
                                .filter(e -> !e.isEmpty())
                                .map(BitcoinNodes.BtcNode::fromFullAddress)
                                .collect(Collectors.toList());
                    }
                    break;
                case PUBLIC:
                    // we keep the empty list
                    break;
                default:
                case PROVIDED:
                    btcNodeList = bitcoinNodes.getProvidedBtcNodes();
                    break;
            }

            final boolean useTorForBitcoinJ = socks5Proxy != null;
            List<PeerAddress> peerAddressList = new ArrayList<>();
            btcNodeList.forEach(btcNode -> {
                        if (useTorForBitcoinJ) {
                            if (btcNode.isHiddenService()) {
                                // no DNS lookup for onion addresses
                                log.info("We add a onion node with btcNode={}", btcNode);
                                final String hostName = btcNode.getHostName();
                                if (hostName != null) {
                                    try {
                                        // OnionCat.onionHostToInetAddress converts onion to ipv6 representation
                                        final InetAddress inetAddress = OnionCat.onionHostToInetAddress(hostName);
                                        final PeerAddress peerAddress = new PeerAddress(hostName, btcNode.getPort());
                                        // inetAddress is not used but required for wallet persistence. Throws nullpointer if not set.
                                        peerAddress.setAddr(inetAddress);
                                        peerAddressList.add(peerAddress);
                                    } catch (UnknownHostException e) {
                                        log.error("OnionCat.onionHostToInetAddress() failed with btcNode={}, error={}", btcNode.toString(), e.toString());
                                        e.printStackTrace();
                                    }
                                } else {
                                    log.error("hostName is null for btcNode={}", btcNode.toString());
                                }
                            } else {
                                try {
                                    // We use DnsLookupTor to not leak with DNS lookup
                                    // Blocking call. takes about 600 ms ;-(
                                    InetSocketAddress address = new InetSocketAddress(DnsLookupTor.lookup(socks5Proxy, btcNode.getHostAddressOrHostName()), btcNode.getPort());
                                    log.info("We add a clear net node (tor is used)  with InetAddress={}, btcNode={}", address.getAddress(), btcNode);
                                    peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                                } catch (Exception e) {
                                    log.warn("Dns lookup failed for btcNode: {}", btcNode);
                                }
                            }
                        } else {
                            // We don't use Tor for BitcoinJ
                            // onion addresses are not supported
                            try {
                                if (!btcNode.isHiddenService()) {
                                    InetSocketAddress address = new InetSocketAddress(btcNode.getHostAddressOrHostName(), btcNode.getPort());
                                    log.info("We add a clear net node (no tor is used) with host={}, btcNode.getPort()={}", btcNode.getHostAddressOrHostName(), btcNode.getPort());
                                    peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                                } else {
                                    log.warn("Onion addresses are only supported when using Tor with BitcoinJ. Ignore btcNode {}", btcNode);
                                }
                            } catch (Throwable t) {
                                log.warn("Failed to create InetSocketAddress from btcNode {}", btcNode);
                            }
                        }
                    }
            );

            if (!peerAddressList.isEmpty()) {
                final PeerAddress[] peerAddresses = peerAddressList.toArray(new PeerAddress[peerAddressList.size()]);
                log.info("You connect with peerAddresses: " + peerAddressList.toString());
                walletConfig.setPeerNodes(peerAddresses);
            } else if (useTorForBitcoinJ) {
                if (params == MainNetParams.get())
                    log.warn("You use the public Bitcoin network and are exposed to privacy issues caused by the broken bloom filters." +
                        "See https://bisq.network/blog/privacy-in-bitsquare/ for more info. It is recommended to use the provided nodes.");
                // SeedPeers uses hard coded stable addresses (from MainNetParams). It should be updated from time to time.
                walletConfig.setDiscovery(new Socks5MultiDiscovery(socks5Proxy, params, socks5DiscoverMode));
            } else {
                log.warn("You don't use gtor and use the public Bitcoin network and are exposed to privacy issues caused by the broken bloom filters." +
                        "See https://bisq.network/blog/privacy-in-bitsquare/ for more info. It is recommended to use Tor and the provided nodes.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Backup
    ///////////////////////////////////////////////////////////////////////////////////////////

    void backupWallets() {
        FileUtil.rollingBackup(walletDir, btcWalletFileName, 20);
        FileUtil.rollingBackup(walletDir, BSQ_WALLET_FILE_NAME, 20);
    }

    void clearBackups() {
        try {
            FileUtil.deleteDirectory(new File(Paths.get(walletDir.getAbsolutePath(), "backup").toString()));
        } catch (IOException e) {
            log.error("Could not delete directory " + e.getMessage());
            e.printStackTrace();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Restore
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void restoreSeedWords(@Nullable DeterministicSeed seed, ResultHandler resultHandler, ExceptionHandler exceptionHandler) {
        checkNotNull(seed, "Seed must be not be null.");

        backupWallets();

        Context ctx = Context.get();
        new Thread(() -> {
            try {
                Context.propagate(ctx);
                walletConfig.stopAsync();
                walletConfig.awaitTerminated();
                initialize(seed, resultHandler, exceptionHandler);
            } catch (Throwable t) {
                t.printStackTrace();
                log.error("Executing task failed. " + t.getMessage());
            }
        }, "RestoreBTCWallet-%d").start();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addSetupCompletedHandler(Runnable handler) {
        setupCompletedHandlers.add(handler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Wallet getBtcWallet() {
        return walletConfig.getBtcWallet();
    }

    @Nullable
    public Wallet getBsqWallet() {
        return walletConfig.getBsqWallet();
    }

    public NetworkParameters getParams() {
        return params;
    }

    public BlockChain getChain() {
        return walletConfig.chain();
    }

    public PeerGroup getPeerGroup() {
        return walletConfig.peerGroup();
    }

    public WalletConfig getWalletConfig() {
        return walletConfig;
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

    public Set<Address> getAddressesByContext(@SuppressWarnings("SameParameterValue") AddressEntry.Context context) {
        return ImmutableList.copyOf(addressEntryList.getList()).stream()
                .filter(addressEntry -> addressEntry.getContext() == context)
                .map(AddressEntry::getAddress)
                .collect(Collectors.toSet());
    }

    public Set<Address> getAddressesFromAddressEntries(Set<AddressEntry> addressEntries) {
        return addressEntries.stream()
                .map(AddressEntry::getAddress)
                .collect(Collectors.toSet());
    }


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
}
