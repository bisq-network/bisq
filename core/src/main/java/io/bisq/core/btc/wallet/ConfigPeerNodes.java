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
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final BtcNodeConverter btcNodeConverter;

    private void configPeerNodes(Socks5Proxy socks5Proxy) {
        List<BtcNode> btcNodeList = getBtcNodes();

        final boolean useTorForBitcoinJ = socks5Proxy != null;
        List<PeerAddress> peerAddressList = getPeerAddresses(socks5Proxy, btcNodeList, useTorForBitcoinJ);

        updateWalletConfig(socks5Proxy, useTorForBitcoinJ, peerAddressList);
    }

    private void updateWalletConfig(Socks5Proxy socks5Proxy, boolean useTorForBitcoinJ, List<PeerAddress> peerAddressList) {
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

    private List<BtcNode> getBtcNodes() {
        List<BtcNode> btcNodeList = new ArrayList<>();

        // We prefer to duplicate the check for CUSTOM here as in case the custom nodes lead to an empty list we fall back to the PROVIDED mode.
        if (preferences.getBitcoinNodesOptionOrdinal() == CUSTOM.ordinal()) {
            btcNodeList = BitcoinNodes.toBtcNodesList(Utilities.commaSeparatedListToSet(preferences.getBitcoinNodes(), false));
            if (btcNodeList.isEmpty()) {
                log.warn("Custom nodes is set but no valid nodes are provided. We fall back to provided nodes option.");
                preferences.setBitcoinNodesOptionOrdinal(BitcoinNodes.BitcoinNodesOption.PROVIDED.ordinal());
            }
        }

        switch (BitcoinNodes.BitcoinNodesOption.values()[preferences.getBitcoinNodesOptionOrdinal()]) {
            case CUSTOM:
                // We have set the btcNodeList already above
                walletConfig.setMinBroadcastConnections((int) Math.ceil(btcNodeList.size() * 0.5));
                // If Tor is set we usually only use onion nodes, but if user provides mixed clear net and onion nodes we want to use both
                break;
            case PUBLIC:
                // We keep the empty btcNodeList
                walletConfig.setMinBroadcastConnections((int) Math.floor(DEFAULT_CONNECTIONS * 0.8));
                break;
            default:
            case PROVIDED:
                btcNodeList = bitcoinNodes.getProvidedBtcNodes();
                // We require only 4 nodes instead of 7 (for 9 max connections) because our provided nodes
                // are more reliable than random public nodes.
                walletConfig.setMinBroadcastConnections(4);
                break;
        }
        return btcNodeList;
    }

    private List<PeerAddress> getPeerAddresses(@Nullable Socks5Proxy socks5Proxy, List<BtcNode> nodes) {
        List<PeerAddress> result;
        // We connect to onion nodes only in case we use Tor for BitcoinJ (default) to avoid privacy leaks at
        // exit nodes with bloom filters.
        if (socks5Proxy != null) {
            List<PeerAddress> onionHosts = nodes.stream()
                    .filter(BtcNode::hasOnionAddress)
                    .flatMap(node -> nullableAsStream(btcNodeConverter.convertOnionHost(node)))
                    .collect(Collectors.toList());

            boolean useCustomNodes = CUSTOM.ordinal() == preferences.getBitcoinNodesOptionOrdinal();
            if (useAllProvidedNodes || useCustomNodes) {
                // We also use the clear net nodes (used for monitor)
                List<PeerAddress> torAddresses = nodes.stream()
                        .filter(BtcNode::hasClearNetAddress)
                        .flatMap(node -> nullableAsStream(btcNodeConverter.convertWithTor(node, socks5Proxy)))
                        .collect(Collectors.toList());

                result = new ArrayList<>();
                result.addAll(onionHosts);
                result.addAll(torAddresses);
            }
        } else {
            result = nodes.stream()
                    .filter(BtcNode::hasClearNetAddress)
                    .flatMap(node -> nullableAsStream(btcNodeConverter.convertClearNode(node)))
                    .collect(Collectors.toList());
        }
        return result;
    }

    private static <T> Stream<T> nullableAsStream(@Nullable T item) {
        return Optional.ofNullable(item)
                .map(Stream::of)
                .orElse(Stream.empty());
    }
}
