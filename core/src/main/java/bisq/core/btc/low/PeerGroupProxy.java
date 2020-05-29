package bisq.core.btc.low;

import org.bitcoinj.core.listeners.PeerDataEventListener;
import org.bitcoinj.core.listeners.PeerConnectedEventListener;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.core.listeners.BlocksDownloadedEventListener;
import org.bitcoinj.core.listeners.PreMessageReceivedEventListener;

import org.bitcoinj.core.Peer;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionBroadcast;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.net.discovery.PeerDiscovery;
import org.bitcoinj.wallet.Wallet;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

abstract class PeerGroupProxy {

    private final org.bitcoinj.core.PeerGroup bitcoinjPeerGroup;

    protected PeerGroupProxy(org.bitcoinj.core.PeerGroup bitcoinjPeerGroup) {
        this.bitcoinjPeerGroup = bitcoinjPeerGroup;
    }

    /* Subset of BitcoinJ API that is used by Bisq: */

    public void start() {
        this.bitcoinjPeerGroup.start();
    }

    protected ListenableFuture startAsync() {
        return this.bitcoinjPeerGroup.startAsync();
    }

    public void stop() {
        this.bitcoinjPeerGroup.stop();
    }

    protected void startBlockChainDownload(PeerDataEventListener listener) {
        this.bitcoinjPeerGroup.startBlockChainDownload(listener);
    }

    public TransactionBroadcast broadcastTransaction(final Transaction tx) {
        return this.bitcoinjPeerGroup.broadcastTransaction(tx);
    }

    protected void addAddress(PeerAddress peerAddress) {
        this.bitcoinjPeerGroup.addAddress(peerAddress);
    }

    public void addPeerDiscovery(PeerDiscovery peerDiscovery) {
        this.bitcoinjPeerGroup.addPeerDiscovery(peerDiscovery);
    }

    public void addWallet(Wallet wallet) {
        this.bitcoinjPeerGroup.addWallet(wallet);
    }

    protected void setUseLocalhostPeerWhenPossible(boolean useLocalhostPeerWhenPossible) {
        this.bitcoinjPeerGroup.setUseLocalhostPeerWhenPossible(useLocalhostPeerWhenPossible);
    }

    protected void setConnectTimeoutMillis(int connectTimeoutMillis) {
        this.bitcoinjPeerGroup.setConnectTimeoutMillis(connectTimeoutMillis);
    }

    public void setMinBroadcastConnections(int value) {
        this.bitcoinjPeerGroup.setMinBroadcastConnections(value);
    }

    public void setUserAgent(String name, String version) {
        this.bitcoinjPeerGroup.setUserAgent(name, version);
    }

    protected void setMaxConnections(int maxConnections) {
        this.bitcoinjPeerGroup.setMaxConnections(maxConnections);
    }

    public void setAddPeersFromAddressMessage(boolean addPeersFromAddressMessage) {
        this.bitcoinjPeerGroup.setAddPeersFromAddressMessage(addPeersFromAddressMessage);
    }

    public static void setIgnoreHttpSeeds(boolean ignoreHttpSeeds) {
        org.bitcoinj.core.PeerGroup.setIgnoreHttpSeeds(ignoreHttpSeeds);
    }

    public void addConnectedEventListener(PeerConnectedEventListener listener) {
        this.bitcoinjPeerGroup.addConnectedEventListener(listener);
    }

    public void addDisconnectedEventListener(PeerDisconnectedEventListener listener) {
        this.bitcoinjPeerGroup.addDisconnectedEventListener(listener);
    }

    public void addBlocksDownloadedEventListener(BlocksDownloadedEventListener listener) {
        this.bitcoinjPeerGroup.addBlocksDownloadedEventListener(listener);
    }

    public void addPreMessageReceivedEventListener(Executor executor, PreMessageReceivedEventListener listener) {
        this.bitcoinjPeerGroup.addPreMessageReceivedEventListener(executor, listener);
    }

    public List<Peer> getConnectedPeers() {
        return this.bitcoinjPeerGroup.getConnectedPeers();
    }

}
