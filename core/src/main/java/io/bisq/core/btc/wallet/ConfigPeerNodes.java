package io.bisq.core.btc.wallet;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.btc.BitcoinNodes.BtcNode;
import io.bisq.core.user.Preferences;
import io.bisq.network.Socks5MultiDiscovery;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.params.MainNetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static io.bisq.core.btc.BitcoinNodes.BitcoinNodesOption.CUSTOM;

class ConfigPeerNodes {
    private static final Logger log = LoggerFactory.getLogger(ConfigPeerNodes.class);

    private static final int DEFAULT_CONNECTIONS;

    private final Preferences preferences;
    private final WalletConfig walletConfig;
    private final NetworkParameters params;
    private final int socks5DiscoverMode;
    private final boolean useAllProvidedNodes;
    private final BitcoinNodes bitcoinNodes;

    ConfigPeerNodes(Preferences preferences, WalletConfig walletConfig, NetworkParameters params,
                    int socks5DiscoverMode, boolean useAllProvidedNodes, BitcoinNodes bitcoinNodes) {
        this.preferences = preferences;
        this.walletConfig = walletConfig;
        this.params = params;
        this.socks5DiscoverMode = socks5DiscoverMode;
        this.useAllProvidedNodes = useAllProvidedNodes;
        this.bitcoinNodes = bitcoinNodes;
    }

    public void configPeerNodes(@Nullable Socks5Proxy proxy) {
        List<BtcNode> nodes = getBtcNodes();
        setUpMinBroadcastConnections(nodes);
        List<PeerAddress> peers = getPeerAddresses(proxy, nodes);
        updateWalletConfig(proxy, peers);
    }

    private void updateWalletConfig(@Nullable Socks5Proxy socks5Proxy, List<PeerAddress> peers) {
        if (!peers.isEmpty()) {
            log.info("You connect with peerAddresses: {}", peers);
            PeerAddress[] peerAddresses = peers.toArray(new PeerAddress[peers.size()]);
            walletConfig.setPeerNodes(peerAddresses);
        } else if (socks5Proxy != null) {
            MainNetParams mainNetParams = MainNetParams.get();
            if (log.isWarnEnabled() && params.equals(mainNetParams)) {
                log.warn("You use the public Bitcoin network and are exposed to privacy issues " +
                        "caused by the broken bloom filters. See https://bisq.network/blog/privacy-in-bitsquare/ " +
                        "for more info. It is recommended to use the provided nodes.");
            }
            // SeedPeers uses hard coded stable addresses (from MainNetParams). It should be updated from time to time.
            walletConfig.setDiscovery(new Socks5MultiDiscovery(socks5Proxy, params, socks5DiscoverMode));
        } else {
            log.warn("You don't use tor and use the public Bitcoin network and are exposed to privacy issues " +
                    "caused by the broken bloom filters. See https://bisq.network/blog/privacy-in-bitsquare/ " +
                    "for more info. It is recommended to use Tor and the provided nodes.");
        }
    }

    private List<BtcNode> getBtcNodes() {
        List<BtcNode> btcNodeList;

        switch (BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()]) {
            case CUSTOM:
                btcNodeList = BitcoinNodes.toBtcNodesList(Utilities.commaSeparatedListToSet(preferences.getBitcoinNodes(), false));
                if (btcNodeList.isEmpty()) {
                    log.warn("Custom nodes is set but no valid nodes are provided. We fall back to provided nodes option.");
                    preferences.setBitcoinNodesOptionOrdinal(BitcoinNodes.BitcoinNodesOption.PROVIDED.ordinal());

                    // TODO refactor as "PROVIDED"
                    btcNodeList = bitcoinNodes.getProvidedBtcNodes();
                }
                break;
            case PUBLIC:
                btcNodeList = Collections.emptyList();
                break;
            case PROVIDED:
            default:
                btcNodeList = bitcoinNodes.getProvidedBtcNodes();
                break;
        }

        return btcNodeList;
    }

    private void setUpMinBroadcastConnections(List<BtcNode> nodes) {
        switch (BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()]) {
            case CUSTOM:
                // We have set the nodes already above
                walletConfig.setMinBroadcastConnections((int) Math.ceil(nodes.size() * 0.5));
                // If Tor is set we usually only use onion nodes,
                // but if user provides mixed clear net and onion nodes we want to use both
                break;
            case PUBLIC:
                // We keep the empty nodes
                walletConfig.setMinBroadcastConnections((int) Math.floor(DEFAULT_CONNECTIONS * 0.8));
                break;
            case PROVIDED:
            default:
                // We require only 4 nodes instead of 7 (for 9 max connections) because our provided nodes
                // are more reliable than random public nodes.
                walletConfig.setMinBroadcastConnections(4);
                break;
        }
    }

    private List<PeerAddress> getPeerAddresses(@Nullable Socks5Proxy proxy, List<BtcNode> nodes) {
        // TODO factory
        PeerAddressesRepository repository = new PeerAddressesRepository(new BtcNodeConverter(), nodes);

        List<PeerAddress> result;
        // We connect to onion nodes only in case we use Tor for BitcoinJ (default) to avoid privacy leaks at
        // exit nodes with bloom filters.
        if (proxy != null) {
            List<PeerAddress> onionHosts = repository.getOnionHosts();
            result = new ArrayList<>(onionHosts);

            if (useAllProvidedNodes || isUseCustomNodes()) {
                // We also use the clear net nodes (used for monitor)
                List<PeerAddress> torAddresses = repository.getProxifiedClearNodes(proxy);
                result.addAll(torAddresses);
            }
        } else {
            result = repository.getClearNodes();
        }
        return result;
    }

    private boolean isUseCustomNodes() {
        return CUSTOM.ordinal() == preferences.getBitcoinNodesOptionOrdinal();
    }
}
