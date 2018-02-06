package io.bisq.core.btc.wallet;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.BitcoinNodes;
import io.bisq.core.user.Preferences;
import io.bisq.network.DnsLookupTor;
import io.bisq.network.Socks5MultiDiscovery;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.net.OnionCat;
import org.bitcoinj.params.MainNetParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

class ConfigPeerNodes {
    private static final Logger log = LoggerFactory.getLogger(ConfigPeerNodes.class);

    private static final int DEFAULT_CONNECTIONS;

    private final Preferences preferences;
    private final WalletConfig walletConfig;
    private final NetworkParameters params;
    private final int socks5DiscoverMode;
    private final boolean useAllProvidedNodes;
    private final BitcoinNodes bitcoinNodes;

    private void configPeerNodes(Socks5Proxy socks5Proxy) {
        List<BitcoinNodes.BtcNode> btcNodeList = getBtcNodes();

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

    private List<BitcoinNodes.BtcNode> getBtcNodes() {
        List<BitcoinNodes.BtcNode> btcNodeList = new ArrayList<>();

        // We prefer to duplicate the check for CUSTOM here as in case the custom nodes lead to an empty list we fall back to the PROVIDED mode.
        if (preferences.getBitcoinNodesOptionOrdinal() == BitcoinNodes.BitcoinNodesOption.CUSTOM.ordinal()) {
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

    private List<PeerAddress> getPeerAddresses(Socks5Proxy socks5Proxy, List<BitcoinNodes.BtcNode> btcNodeList, boolean useTorForBitcoinJ) {
        boolean useCustomNodes = BitcoinNodes.BitcoinNodesOption.CUSTOM.ordinal() == preferences.getBitcoinNodesOptionOrdinal();

        List<PeerAddress> peerAddressList = new ArrayList<>();
        // We connect to onion nodes only in case we use Tor for BitcoinJ (default) to avoid privacy leaks at
        // exit nodes with bloom filters.
        if (useTorForBitcoinJ) {
            btcNodeList.stream()
                    .filter(BitcoinNodes.BtcNode::hasOnionAddress)
                    .forEach(btcNode -> {
                        // no DNS lookup for onion addresses
                        log.info("We add a onion node. btcNode={}", btcNode);
                        final String onionAddress = checkNotNull(btcNode.getOnionAddress());
                        try {
                            // OnionCat.onionHostToInetAddress converts onion to ipv6 representation
                            // inetAddress is not used but required for wallet persistence. Throws nullPointer if not set.
                            final InetAddress inetAddress = OnionCat.onionHostToInetAddress(onionAddress);
                            final PeerAddress peerAddress = new PeerAddress(onionAddress, btcNode.getPort());
                            peerAddress.setAddr(inetAddress);
                            peerAddressList.add(peerAddress);
                        } catch (UnknownHostException e) {
                            log.error("OnionCat.onionHostToInetAddress() failed with btcNode={}, error={}", btcNode.toString(), e.toString());
                            e.printStackTrace();
                        }
                    });
            if (useAllProvidedNodes || useCustomNodes) {
                // We also use the clear net nodes (used for monitor)
                btcNodeList.stream()
                        .filter(BitcoinNodes.BtcNode::hasClearNetAddress)
                        .forEach(btcNode -> {
                            try {
                                // We use DnsLookupTor to not leak with DNS lookup
                                // Blocking call. takes about 600 ms ;-(
                                InetSocketAddress address = new InetSocketAddress(DnsLookupTor.lookup(socks5Proxy, btcNode.getHostNameOrAddress()), btcNode.getPort());
                                log.info("We add a clear net node (tor is used)  with InetAddress={}, btcNode={}", address.getAddress(), btcNode);
                                peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                            } catch (Exception e) {
                                if (btcNode.getAddress() != null) {
                                    log.warn("Dns lookup failed. We try with provided IP address. BtcNode: {}", btcNode);
                                    try {
                                        InetSocketAddress address = new InetSocketAddress(DnsLookupTor.lookup(socks5Proxy, btcNode.getAddress()), btcNode.getPort());
                                        log.info("We add a clear net node (tor is used)  with InetAddress={}, BtcNode={}", address.getAddress(), btcNode);
                                        peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                                    } catch (Exception e2) {
                                        log.warn("Dns lookup failed for BtcNode: {}", btcNode);
                                    }
                                } else {
                                    log.warn("Dns lookup failed. No IP address is provided. BtcNode: {}", btcNode);
                                }
                            }
                        });
            }
        } else {
            btcNodeList.stream()
                    .filter(BitcoinNodes.BtcNode::hasClearNetAddress)
                    .forEach(btcNode -> {
                        try {
                            InetSocketAddress address = new InetSocketAddress(btcNode.getHostNameOrAddress(), btcNode.getPort());
                            log.info("We add a clear net node (no tor is used) with host={}, btcNode.getPort()={}", btcNode.getHostNameOrAddress(), btcNode.getPort());
                            peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                        } catch (Throwable t) {
                            if (btcNode.getAddress() != null) {
                                log.warn("Dns lookup failed. We try with provided IP address. BtcNode: {}", btcNode);
                                try {
                                    InetSocketAddress address = new InetSocketAddress(btcNode.getAddress(), btcNode.getPort());
                                    log.info("We add a clear net node (no tor is used) with host={}, btcNode.getPort()={}", btcNode.getHostNameOrAddress(), btcNode.getPort());
                                    peerAddressList.add(new PeerAddress(address.getAddress(), address.getPort()));
                                } catch (Throwable t2) {
                                    log.warn("Failed to create InetSocketAddress from btcNode {}", btcNode);
                                }
                            } else {
                                log.warn("Dns lookup failed. No IP address is provided. BtcNode: {}", btcNode);
                            }
                        }
                    });
        }
        return peerAddressList;
    }

}
