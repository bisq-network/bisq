package bisq.core.btc.low;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.net.BlockingClientManager;

import bisq.core.btc.nodes.ProxySocketFactory;
import bisq.core.btc.nodes.LocalBitcoinNode;

import bisq.common.config.Config;

import java.net.Proxy;
import java.net.InetSocketAddress;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

final public class PeerGroup extends PeerGroupProxy {

    // These constructors will be subsequently factored out of this class

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

    public static PeerGroup createPeerGroup(
            Socks5Proxy socks5Proxy,
            NetworkParameters params,
            BlockChain vChain,
            LocalBitcoinNode localBitcoinNode,
            Config config,
            int torSocketTimeout,
            int torVersionExchangeTimeout
    ) {
        PeerGroup peerGroup;
        // no proxy case.
        if (socks5Proxy == null) {
            peerGroup = new PeerGroup(params, vChain);
        } else {
            // proxy case (tor).
            Proxy proxy = new Proxy(Proxy.Type.SOCKS,
                    new InetSocketAddress(
                        socks5Proxy.getInetAddress().getHostName(),
                        socks5Proxy.getPort()));

            ProxySocketFactory proxySocketFactory =
                new ProxySocketFactory(proxy);
            // We don't use tor mode if we have a local node running
            BlockingClientManager blockingClientManager =
                config.ignoreLocalBtcNode ?
                new BlockingClientManager() :
                new BlockingClientManager(proxySocketFactory);

            peerGroup = new PeerGroup(params, vChain, blockingClientManager);

            blockingClientManager.setConnectTimeoutMillis(torSocketTimeout);
            peerGroup.setConnectTimeoutMillis(torVersionExchangeTimeout);
        }

        if (!localBitcoinNode.shouldBeUsed())
            peerGroup.setUseLocalhostPeerWhenPossible(false);

        return peerGroup;
    }

}
