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
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.core.user.Preferences;
import io.bisq.network.Socks5MultiDiscovery;
import io.bisq.network.Socks5ProxyProvider;
import javafx.beans.property.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.utils.Threading;
import org.bitcoinj.wallet.DeterministicSeed;
import org.bitcoinj.wallet.Wallet;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import javax.inject.Named;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
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
@Slf4j
public class WalletsSetup {
    // We reduce defaultConnections from 12 (PeerGroup.DEFAULT_CONNECTIONS) to 9 nodes
    static final int DEFAULT_CONNECTIONS = 9;

    private static final long STARTUP_TIMEOUT = 180;
    private static final String BSQ_WALLET_FILE_NAME = "bisq_BSQ.wallet";
    private static final String SPV_CHAIN_FILE_NAME = "bisq.spvchain";

    private final RegTestHost regTestHost;
    private final AddressEntryList addressEntryList;
    private final Preferences preferences;
    private final Socks5ProxyProvider socks5ProxyProvider;
    private final BisqEnvironment bisqEnvironment;
    private final BitcoinNodes bitcoinNodes;
    private final String btcWalletFileName;
    private final int numConnectionForBtc;
    private final String userAgent;
    private final NetworkParameters params;
    private final File walletDir;
    private final int socks5DiscoverMode;
    private final IntegerProperty numPeers = new SimpleIntegerProperty(0);
    private final ObjectProperty<List<Peer>> connectedPeers = new SimpleObjectProperty<>();
    private final DownloadListener downloadListener = new DownloadListener();
    private final List<Runnable> setupCompletedHandlers = new ArrayList<>();
    public final BooleanProperty shutDownComplete = new SimpleBooleanProperty();
    private final boolean useAllProvidedNodes;
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
                        @Named(BtcOptionKeys.USER_AGENT) String userAgent,
                        @Named(BtcOptionKeys.WALLET_DIR) File appDir,
                        @Named(BtcOptionKeys.USE_ALL_PROVIDED_NODES) String useAllProvidedNodes,
                        @Named(BtcOptionKeys.NUM_CONNECTIONS_FOR_BTC) String numConnectionForBtc,
                        @Named(BtcOptionKeys.SOCKS5_DISCOVER_MODE) String socks5DiscoverModeString) {
        this.regTestHost = regTestHost;
        this.addressEntryList = addressEntryList;
        this.preferences = preferences;
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.bisqEnvironment = bisqEnvironment;
        this.bitcoinNodes = bitcoinNodes;
        this.numConnectionForBtc = numConnectionForBtc != null ? Integer.parseInt(numConnectionForBtc) : DEFAULT_CONNECTIONS;
        this.useAllProvidedNodes = "true".equals(useAllProvidedNodes);
        this.userAgent = userAgent;

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
                        STARTUP_TIMEOUT + " seconds.")), STARTUP_TIMEOUT);

        backupWallets();

        final Socks5Proxy socks5Proxy = preferences.getUseTorForBitcoinJ() ? socks5ProxyProvider.getSocks5Proxy() : null;
        log.info("Socks5Proxy for bitcoinj: socks5Proxy=" + socks5Proxy);

        walletConfig = new WalletConfig(params,
                socks5Proxy,
                walletDir,
                bisqEnvironment,
                userAgent,
                numConnectionForBtc,
                btcWalletFileName,
                BSQ_WALLET_FILE_NAME,
                SPV_CHAIN_FILE_NAME) {
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

        if (params == RegTestParams.get()) {
            configPeerNodesForRegTest();
        } else if (bisqEnvironment.isBitcoinLocalhostNodeRunning()) {
            configPeerNodesForLocalHostBitcoinNode();
        } else {
            configPeerNodes(socks5Proxy);
        }

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

    public void reSyncSPVChain() throws IOException {
        FileUtil.deleteFileIfExists(new File(walletDir, SPV_CHAIN_FILE_NAME));
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

    private void configPeerNodesForRegTest() {
        walletConfig.setMinBroadcastConnections(1);
        if (regTestHost == RegTestHost.REG_TEST_SERVER) {
            try {
                walletConfig.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
            } catch (UnknownHostException e) {
                log.error(e.toString());
                e.printStackTrace();
                throw new RuntimeException(e);
            }
        } else if (regTestHost == RegTestHost.LOCALHOST) {
            walletConfig.setPeerNodesForLocalHost();
        }
    }

    private void configPeerNodesForLocalHostBitcoinNode() {
        walletConfig.setMinBroadcastConnections(1);
        walletConfig.setPeerNodesForLocalHost();
    }

    private void configPeerNodes(@Nullable Socks5Proxy proxy) {
        WalletSetupPreferences walletSetupPreferences = new WalletSetupPreferences(preferences);

        List<BtcNode> nodes = walletSetupPreferences.selectPreferredNodes(bitcoinNodes);
        int minBroadcastConnections = walletSetupPreferences.calculateMinBroadcastConnections(nodes);
        walletConfig.setMinBroadcastConnections(minBroadcastConnections);

        PeerAddressesRepository repository = new PeerAddressesRepository(nodes);
        boolean isUseClearNodesWithProxies = (useAllProvidedNodes || walletSetupPreferences.isUseCustomNodes());
        List<PeerAddress> peers = repository.getPeerAddresses(proxy, isUseClearNodesWithProxies);

        WalletNetworkConfig networkConfig = new WalletNetworkConfig(walletConfig, params, socks5DiscoverMode, proxy);
        networkConfig.proposePeers(peers);
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

    @Nullable
    public BlockChain getChain() {
        return walletConfig != null ? walletConfig.chain() : null;
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

    public boolean isDownloadComplete() {
        return downloadPercentageProperty().get() == 1d;
    }

    public boolean isBitcoinLocalhostNodeRunning() {
        return bisqEnvironment.isBitcoinLocalhostNodeRunning();
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

    public boolean hasSufficientPeersForBroadcast() {
        return numPeers.get() >= getMinBroadcastConnections();
    }

    public int getMinBroadcastConnections() {
        return walletConfig.getMinBroadcastConnections();
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
