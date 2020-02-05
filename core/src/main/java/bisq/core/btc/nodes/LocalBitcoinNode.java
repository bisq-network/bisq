package bisq.core.btc.nodes;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import java.net.InetSocketAddress;
import java.net.Socket;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether a Bitcoin node is running on localhost.
 * @see bisq.common.config.Config#ignoreLocalBtcNode
 */
@Singleton
public class LocalBitcoinNode {

    public static final String LOCAL_BITCOIN_NODE_PORT = "localBitcoinNodePort";

    private static final Logger log = LoggerFactory.getLogger(LocalBitcoinNode.class);
    private static final int CONNECTION_TIMEOUT = 5000;

    private final int port;
    private boolean detected = false;

    @Inject
    public LocalBitcoinNode(@Named(LOCAL_BITCOIN_NODE_PORT) int port) {
        this.port = port;
    }

    /**
     * Detect whether a Bitcoin node is running on localhost by attempting to connect
     * to the node's port and run the given callback regardless of whether the connection
     * was successful. If the connection is successful, subsequent calls to
     * {@link #isDetected()} will return {@code true}.
     * @param callback code to run after detecting whether the node is running
     */
    public void detectAndRun(Runnable callback) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress("127.0.0.1", port), CONNECTION_TIMEOUT);
            log.info("Local Bitcoin node detected on port {}", port);
            detected = true;
        } catch (IOException ex) {
            log.info("No local Bitcoin node detected on port {}.", port);
        }
        callback.run();
    }

    /**
     * Returns whether or not a Bitcoin node was running on localhost at the time
     * {@link #detectAndRun(Runnable)} was called. No further monitoring is performed, so
     * if the node goes up or down in the meantime, this method will continue to return
     * its original value. See {@code MainViewModel#setupBtcNumPeersWatcher} to understand
     * how disconnection and reconnection of the local Bitcoin node is actually handled.
     */
    public boolean isDetected() {
        return detected;
    }
}
