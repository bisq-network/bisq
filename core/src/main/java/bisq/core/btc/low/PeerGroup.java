package bisq.core.btc.low;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.params.RegTestParams;

import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.DnsDiscovery;

import bisq.core.btc.nodes.ProxySocketFactory;
import bisq.core.btc.nodes.LocalBitcoinNode;

import bisq.common.config.Config;

import java.net.Proxy;
import java.net.InetSocketAddress;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

final public class PeerGroup extends PeerGroupProxy {

    private PeerGroup(
            NetworkParameters params,
            BlockChain vChain
    ) {
        super(new org.bitcoinj.core.PeerGroup(params, vChain));
    }

    private PeerGroup(
            NetworkParameters params,
            BlockChain vChain,
            BlockingClientManager blockingClientManager
    ) {
        super(new org.bitcoinj.core.PeerGroup(params, vChain, blockingClientManager));
    }

    /* major elements in this method:
     * - a proxy / no proxy
     * - tor / no tor
     * - should local / remote bitcoin node be used
     * - config says / doesn't say to ignore local node when found
     * - blocking / async client manager
     *
     * Could use model checking to check if logic is correct;
     * would be especially necessary when changing it.
     */
    public static PeerGroup createPeerGroup(
            Socks5Proxy socks5Proxy,
            NetworkParameters params,
            BlockChain vChain,
            LocalBitcoinNode localBitcoinNode,
            int torSocketTimeout,
            int torVersionExchangeTimeout
    ) {
        PeerGroup peerGroup;
        if (localBitcoinNode.shouldBeUsed()) {
            peerGroup = new PeerGroup(params, vChain);
        } else {
            var notUsingProxy = socks5Proxy == null;
            if (notUsingProxy) {
                // no proxy case.
                peerGroup = new PeerGroup(params, vChain);
            } else {
                // proxy case (tor).
                Proxy proxy = new Proxy(
                        Proxy.Type.SOCKS,
                        new InetSocketAddress(
                            socks5Proxy.getInetAddress().getHostName(),
                            socks5Proxy.getPort()
                            ));
                ProxySocketFactory proxySocketFactory =
                    new ProxySocketFactory(proxy);
                BlockingClientManager blockingClientManager =
                    new BlockingClientManager(proxySocketFactory);

                peerGroup = new PeerGroup(params, vChain, blockingClientManager);

                blockingClientManager.setConnectTimeoutMillis(torSocketTimeout);
                peerGroup.setConnectTimeoutMillis(torVersionExchangeTimeout);
            }
        peerGroup.setUseLocalhostPeerWhenPossible(false);
        }

        return peerGroup;
    }

    public void setupPeerAddressesOrDiscovery(
            PeerAddress[] peerAddresses,
            int numConnectionsForBtc,
            NetworkParameters params,
            PeerDiscovery discovery
    ) {
        if (peerAddresses != null) {
            for (PeerAddress addr : peerAddresses) this.addAddress(addr);
            int maxConnections = Math.min(numConnectionsForBtc, peerAddresses.length);
            //log.info("We try to connect to {} btc nodes", maxConnections);
            this.setMaxConnections(maxConnections);
            this.setAddPeersFromAddressMessage(false);
            peerAddresses = null;
        } else if (!params.equals(RegTestParams.get())) {
            this.addPeerDiscovery(discovery != null ? discovery : new DnsDiscovery(params));
        }

    }

}
