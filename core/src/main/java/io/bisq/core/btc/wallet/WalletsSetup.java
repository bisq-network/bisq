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
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.AddressEntryList;
import io.bisq.core.btc.BtcOptionKeys;
import io.bisq.core.btc.RegTestHost;
import io.bisq.core.user.Preferences;
import io.bisq.network.DnsLookupTor;
import io.bisq.network.Socks5MultiDiscovery;
import io.bisq.network.Socks5ProxyProvider;
import javafx.beans.property.*;
import org.apache.commons.lang3.StringUtils;
import org.bitcoinj.core.*;
import org.bitcoinj.core.listeners.DownloadProgressTracker;
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
import java.util.*;
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
                        @Named(BtcOptionKeys.WALLET_DIR) File appDir,
                        @Named(BtcOptionKeys.SOCKS5_DISCOVER_MODE) String socks5DiscoverModeString) {
        this.regTestHost = regTestHost;
        this.addressEntryList = addressEntryList;
        this.preferences = preferences;
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.bisqEnvironment = bisqEnvironment;

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
        log.debug("Use socks5Proxy for bitcoinj: " + socks5Proxy);

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
            } catch (Throwable e) {
                // ignore
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
        String btcNodes = preferences.getBitcoinNodes();
        log.debug("btcNodes: " + btcNodes);
        boolean usePeerNodes = false;

        // Pass custom seed nodes if set in options
        if (!btcNodes.isEmpty()) {
            String[] nodes = StringUtils.deleteWhitespace(btcNodes).split(",");
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
                    // note: .onion hostnames will be unresolved.
                    InetSocketAddress addr;
                    if (socks5Proxy != null) {
                        try {
                            // proxy remote DNS request happens here.  blocking.
                            addr = new InetSocketAddress(DnsLookupTor.lookup(socks5Proxy, parts[0]), Integer.parseInt(parts[1]));
                        } catch (Exception e) {
                            log.warn("Dns lookup failed for host: {}", parts[0]);
                            addr = null;
                        }
                    } else {
                        // DNS request happens here. if it fails, addr.isUnresolved() == true.
                        addr = new InetSocketAddress(parts[0], Integer.parseInt(parts[1]));
                    }
                    if (addr != null && !addr.isUnresolved()) {
                        peerAddressList.add(new PeerAddress(addr.getAddress(), addr.getPort()));
                    }
                }
                if (peerAddressList.size() > 0) {
                    PeerAddress peerAddressListFixed[] = new PeerAddress[peerAddressList.size()];
                    log.debug("btcNodes parsed: " + Arrays.toString(peerAddressListFixed));

                    walletConfig.setPeerNodes(peerAddressList.toArray(peerAddressListFixed));
                    usePeerNodes = true;
                }
            }
        }

        // Now configure and start the appkit. This will take a second or two - we could show a temporary splash screen
        // or progress widget to keep the user engaged whilst we initialise, but we don't.
        if (params == RegTestParams.get()) {
            if (regTestHost == RegTestHost.REG_TEST_SERVER) {
                try {
                    walletConfig.setPeerNodes(new PeerAddress(InetAddress.getByName(RegTestHost.SERVER_IP), params.getPort()));
                    usePeerNodes = true;
                } catch (UnknownHostException e) {
                    throw new RuntimeException(e);
                }
            } else if (regTestHost == RegTestHost.LOCALHOST) {
                walletConfig.connectToLocalHost();   // You should run a regtest mode bitcoind locally.}
            }
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
        if (socks5Proxy != null && !usePeerNodes && regTestHost != RegTestHost.LOCALHOST) {
            // SeedPeers uses hard coded stable addresses (from MainNetParams). It should be updated from time to time.
            walletConfig.setDiscovery(new Socks5MultiDiscovery(socks5Proxy, params, socks5DiscoverMode));
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
