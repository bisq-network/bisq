package bisq.core.btc.nodes;

import bisq.common.config.BaseCurrencyNetwork;
import bisq.common.config.Config;

import org.bitcoinj.core.BitcoinSerializer;
import org.bitcoinj.core.MessageSerializer;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.ProtocolException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VersionMessage;

import com.google.common.annotations.VisibleForTesting;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import java.nio.ByteBuffer;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/**
 * Detects whether a Bitcoin node is running on localhost and contains logic for when to
 * ignore it. The check starts when the instance is created, and its result is kept.
 * @see bisq.common.config.Config#ignoreLocalBtcNode
 */
@Singleton
public class LocalBitcoinNode {

    private static final Logger log = LoggerFactory.getLogger(LocalBitcoinNode.class);
    private static final int CONNECTION_TIMEOUT = 5000;
    private static final int MAX_VERSION_MESSAGE_SIZE = 1024;

    private final Config config;
    @Nullable
    private final CompletableFuture<Detection> detection;

    @Inject
    public LocalBitcoinNode(Config config) {
        this(config, config.networkParameters.getPort());
    }

    // The check can take up to 10 seconds, so it starts at once on its own thread. The first caller waits only for
    // the rest of it, and does not block the start of the application for the whole time.
    @VisibleForTesting
    LocalBitcoinNode(Config config, int port) {
        this.config = config;
        if (shouldBeIgnored()) {
            detection = null;
        } else {
            NetworkParameters params = config.networkParameters;
            detection = CompletableFuture.supplyAsync(() -> detect(params, port), runnable -> {
                Thread thread = new Thread(runnable, "LocalBitcoinNode");
                thread.setDaemon(true);
                thread.start();
            });
        }
    }

    /**
     * Returns whether Bisq should use a local Bitcoin node, meaning that a usable node was
     * detected and conditions under which it should be ignored have not been met. If
     * the local node should be ignored, a call to this method will not trigger an
     * unnecessary detection attempt.
     */
    public boolean shouldBeUsed() {
        return !shouldBeIgnored() && isUsable();
    }

    /**
     * Returns whether Bisq should ignore a local Bitcoin node even if it is usable.
     */
    public boolean shouldBeIgnored() {
        BaseCurrencyNetwork baseCurrencyNetwork = config.getBaseCurrencyNetwork();

        // For dao testnet (server side regtest) we disable the use of local bitcoin node
        // to avoid confusion if local btc node is not synced with our dao testnet master
        // node. Note: above comment was previously in WalletConfig::createPeerGroup.
        return config.ignoreLocalBtcNode ||
                baseCurrencyNetwork.isDaoRegTest() ||
                baseCurrencyNetwork.isDaoTestNet();
    }

    /**
     * Returns why the wallet cannot use the local Bitcoin node that was found, or empty if no node was found, it can be
     * used, or it is ignored. On {@code BTC_REGTEST} the peer of the wallet is set with {@code --bitcoinRegtestHost},
     * so the result is always empty there.
     */
    public Optional<String> getUnusableReason() {
        if (detection == null || config.getBaseCurrencyNetwork().isRegtest()) {
            return Optional.empty();
        }
        return Optional.ofNullable(detection.join().unusableReason());
    }

    /**
     * Returns whether a usable local Bitcoin node was detected. No further monitoring is performed, so if the node
     * goes up or down in the meantime, this method will continue to return its original value. See
     * {@code MainViewModel#setupBtcNumPeersWatcher} to understand how disconnection and reconnection of the local
     * Bitcoin node is actually handled.
     */
    private boolean isUsable() {
        return detection != null && detection.join().isUsable();
    }

    // The result of looking for a local node: whether one was found, and if the wallet cannot use it, why not
    @VisibleForTesting
    record Detection(boolean found, @Nullable String unusableReason) {
        boolean isUsable() {
            return found && unusableReason == null;
        }
    }

    /**
     * Detect whether a Bitcoin node is running on localhost by attempting to connect
     * to the node's port, and whether the wallet can use it as its only peer.
     */
    @VisibleForTesting
    static Detection detect(NetworkParameters params, int port) {
        Socket socket = new Socket();
        try {
            try {
                socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECTION_TIMEOUT);
            } catch (IOException ex) {
                log.info("No local Bitcoin node detected on port {}.", port);
                return new Detection(false, null);
            }
            log.info("Local Bitcoin node detected on port {}", port);
            return new Detection(true, findUnusableReason(params, socket));
        } finally {
            // The result is known at this point, a failure to close does not change it
            try {
                socket.close();
            } catch (IOException ignore) {
            }
        }
    }

    // The local node is the only peer of the wallet. bitcoinj downloads blocks only from a peer
    // that serves the full block chain and witness data, and it disconnects a peer without bloom
    // filters. A pruned node announces NODE_NETWORK_LIMITED instead of NODE_NETWORK.
    // Returns null if the wallet can use the node.
    @Nullable
    private static String findUnusableReason(NetworkParameters params, Socket socket) {
        VersionMessage versionMessage;
        try {
            versionMessage = readVersionMessage(params, socket);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the version message of the local Bitcoin node, " +
                    "so we use it without knowing its services. {}", e.toString());
            return null;
        }
        List<String> missing = new ArrayList<>();
        if (!versionMessage.hasBlockChain()) {
            missing.add("it does not serve the full block chain, for example because it is pruned");
        }
        if (!versionMessage.isWitnessSupported()) {
            missing.add("it does not serve witness data");
        }
        if (!versionMessage.isBloomFilteringSupported()) {
            missing.add("bloom filters are not enabled (peerbloomfilters=1)");
        }
        if (missing.isEmpty()) {
            return null;
        }
        String reason = String.join("; ", missing);
        log.warn("The local Bitcoin node cannot serve the wallet: {} (services: {}).",
                reason, VersionMessage.toStringServices(versionMessage.localServices));
        return reason;
    }

    // A node answers the version message of a new peer with its own version message.
    private static VersionMessage readVersionMessage(NetworkParameters params, Socket socket) throws IOException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(CONNECTION_TIMEOUT);
        MessageSerializer serializer = params.getDefaultSerializer();
        serializer.serialize(new VersionMessage(params, 0), socket.getOutputStream());

        InputStream in = socket.getInputStream();
        byte[] magic = new byte[4];
        readFully(socket, in, magic, deadline);
        if (Utils.readUint32BE(magic, 0) != params.getPacketMagic()) {
            throw new ProtocolException("The answer is not a Bitcoin message of this network");
        }
        byte[] headerBytes = new byte[BitcoinSerializer.BitcoinPacketHeader.HEADER_LENGTH];
        readFully(socket, in, headerBytes, deadline);
        BitcoinSerializer.BitcoinPacketHeader header = serializer.deserializeHeader(ByteBuffer.wrap(headerBytes));
        if (!header.command.equals("version")) {
            throw new ProtocolException("Expected a version message, got " + header.command);
        }
        // A version message has about 130 bytes, its user agent at most 256
        if (header.size > MAX_VERSION_MESSAGE_SIZE) {
            throw new ProtocolException("The version message announces " + header.size + " bytes");
        }
        byte[] payload = new byte[header.size];
        readFully(socket, in, payload, deadline);
        return (VersionMessage) serializer.deserializePayload(header, ByteBuffer.wrap(payload));
    }

    // The socket timeout applies to each read, so a node sending a byte now and then would keep us
    // waiting. The deadline limits the time for the whole answer.
    private static void readFully(Socket socket, InputStream in, byte[] buffer, long deadline) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
            if (remaining <= 0) {
                throw new SocketTimeoutException("No complete version message within " + CONNECTION_TIMEOUT + " ms");
            }
            socket.setSoTimeout((int) remaining);
            int read = in.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                throw new EOFException("The connection was closed before the version message was complete");
            }
            offset += read;
        }
    }
}
