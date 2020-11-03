package bisq.core.btc.nodes;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether a Bitcoin node is running on localhost and contains logic for when to
 * ignore it. The query methods lazily trigger the needed checks and cache the results.
 * @see bisq.common.config.Config#ignoreLocalBtcNode
 */
@Singleton
public class LocalBitcoinNode {

    private static final Logger log = LoggerFactory.getLogger(LocalBitcoinNode.class);
    private static final int CONNECTION_TIMEOUT = 5000;

    private final Config config;
    private final int port;

    private Boolean detected;

    @Inject
    public LocalBitcoinNode(Config config) {
        this.config = config;
        this.port = config.networkParameters.getPort();
    }

    /**
     * Returns whether Bisq should use a local Bitcoin node, meaning that a node was
     * detected and conditions under which it should be ignored have not been met. If
     * the local node should be ignored, a call to this method will not trigger an
     * unnecessary detection attempt.
     */
    public boolean shouldBeUsed() {
        return !shouldBeIgnored() && isDetected();
    }

    /**
     * Returns whether Bisq should ignore a local Bitcoin node even if it is usable.
     */
    public boolean shouldBeIgnored() {
        BaseCurrencyNetwork baseCurrencyNetwork = config.baseCurrencyNetwork;

        // For dao testnet (server side regtest) we disable the use of local bitcoin node
        // to avoid confusion if local btc node is not synced with our dao testnet master
        // node. Note: above comment was previously in WalletConfig::createPeerGroup.
        return config.ignoreLocalBtcNode ||
                baseCurrencyNetwork.isDaoRegTest() ||
                baseCurrencyNetwork.isDaoTestNet();
    }

    /**
     * Returns whether a local Bitcoin node was detected. The check is triggered in case
     * it has not been performed. No further monitoring is performed, so if the node
     * goes up or down in the meantime, this method will continue to return its original
     * value. See {@code MainViewModel#setupBtcNumPeersWatcher} to understand how
     * disconnection and reconnection of the local Bitcoin node is actually handled.
     */
    private boolean isDetected() {
        if (detected == null) {
            detected = detect(port);
        }
        return detected;
    }

    /**
     * Detect whether a Bitcoin node is running on localhost by attempting to connect
     * to the node's port.
     */
    private static boolean detect(int port) {
        try (Socket socket = new Socket()) {
            var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            socket.connect(address, CONNECTION_TIMEOUT);
            log.info("Local Bitcoin node detected on port {}", port);
            return true;
        } catch (IOException ex) {
            log.info("No local Bitcoin node detected on port {}.", port);
            return false;
        }
    }

}
