package bisq.core.btc.low;

import org.bitcoinj.core.BlockChain;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.listeners.DownloadProgressTracker;

import org.bitcoinj.params.RegTestParams;

import org.bitcoinj.net.BlockingClientManager;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.net.discovery.DnsDiscovery;
import org.bitcoinj.net.ClientConnectionManager;

import bisq.core.btc.nodes.ProxySocketFactory;
import bisq.core.btc.nodes.LocalBitcoinNode;

import bisq.common.config.Config;

import java.net.Proxy;
import java.net.InetSocketAddress;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.ListenableFuture;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

final public class PeerGroup extends PeerGroupProxy {

    private static final Logger log = LoggerFactory.getLogger(PeerGroup.class);

    private PeerGroup(
            NetworkParameters params,
            BlockChain vChain
    ) {
        super(new org.bitcoinj.core.PeerGroup(params, vChain));
    }

    private PeerGroup(
            NetworkParameters params,
            BlockChain vChain,
            ClientConnectionManager clientConnectionManager
    ) {
        super(new org.bitcoinj.core.PeerGroup(params, vChain, clientConnectionManager));
    }

    public static PeerGroup createLocalPeerGroup(
            NetworkParameters params,
            BlockChain vChain
    ) {
        return new PeerGroup(params, vChain);
    }

    public static PeerGroup createRemotePeerGroup(
            NetworkParameters params,
            BlockChain vChain,
            Socks5Proxy socks5Proxy,
            int torSocketTimeout,
            int torVersionExchangeTimeout
    ) {
        PeerGroup peerGroup;
        var notUsingProxy = socks5Proxy == null;
        if (notUsingProxy) {
            peerGroup = new PeerGroup(params, vChain);
        } else {
            ClientConnectionManager socks5ProxyClientConnectionManager =
                getSocks5ProxyClientConnectionManager(
                        socks5Proxy, torSocketTimeout);
            peerGroup = new PeerGroup(
                    params, vChain, socks5ProxyClientConnectionManager);
            peerGroup.setConnectTimeoutMillis(torVersionExchangeTimeout);
        }
        // Keep remote PeerGroup from using a local BTC node.
        peerGroup.setUseLocalhostPeerWhenPossible(false);
        return peerGroup;
    }

    /* This method returns the more general ClientConnectionManager (instead of
     * BlockingClientManager), presuming that the rest of the code shouldn't
     * care if we're using a blocking or an async connection manager.
     */
    private static ClientConnectionManager getSocks5ProxyClientConnectionManager(
            Socks5Proxy socks5Proxy,
            int torSocketTimeout
    ) {
        var inetAddress = socks5Proxy.getInetAddress().getHostName();
        var port = socks5Proxy.getPort();
        Proxy proxy = new Proxy(
                Proxy.Type.SOCKS,
                new InetSocketAddress(inetAddress, port));
        ProxySocketFactory proxySocketFactory =
            new ProxySocketFactory(proxy);
        BlockingClientManager blockingClientManager =
            new BlockingClientManager(proxySocketFactory);
        blockingClientManager.setConnectTimeoutMillis(torSocketTimeout);
        return blockingClientManager;
    }

    public void setCustomPeersToBeUsedExclusively(
            PeerAddress[] peerAddresses,
            int numConnectionsForBtc
    ) {
        for (PeerAddress addr : peerAddresses) this.addAddress(addr);
        int maxConnections = Math.min(numConnectionsForBtc, peerAddresses.length);
        log.info("We try to connect to {} btc nodes", maxConnections);
        this.setMaxConnections(maxConnections);
        this.setAddPeersFromAddressMessage(false);
        // TODO what is the purpose of nulling/mutating this input variable?
        peerAddresses = null;
    }

    /* Ideally we would just run PeerGroup.startAsync() and have a method for
     * attaching a download tracker separately, but that would require
     * switching to using PeerGroup lifecycle hooks, which can be finicky and
     * difficult to understand, so we leave it as is for the moment. */
    public ListenableFuture startAsyncWithDownloadTracker(
            DownloadProgressTracker passedDownloadTracker
    ) throws InterruptedException {
        DownloadProgressTracker downloadTracker =
            passedDownloadTracker == null ?
            new DownloadProgressTracker() : passedDownloadTracker;
        ListenableFuture startFuture = this.startAsync();
        Futures.addCallback(
                startFuture,
                new FutureCallback<Object>() {
                    @Override
                    public void onSuccess(@Nullable Object result) {
                        PeerGroup.this.startBlockChainDownload(downloadTracker);
                    }
                    @Override
                    public void onFailure(@NotNull Throwable t) {
                        throw new RuntimeException(t);
                    }});
        return startFuture;
    }

}
