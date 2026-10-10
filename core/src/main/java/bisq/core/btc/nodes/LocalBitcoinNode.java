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

import java.nio.ByteBuffer;

import java.io.DataInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

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
    private final NetworkParameters params;

    private Boolean usable;

    @Inject
    public LocalBitcoinNode(Config config) {
        this.config = config;
        this.params = config.networkParameters;
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
     * Returns whether a usable local Bitcoin node was detected. The check is triggered in
     * case it has not been performed. No further monitoring is performed, so if the node
     * goes up or down in the meantime, this method will continue to return its original
     * value. See {@code MainViewModel#setupBtcNumPeersWatcher} to understand how
     * disconnection and reconnection of the local Bitcoin node is actually handled.
     */
    private boolean isUsable() {
        if (usable == null) {
            usable = detect(params, params.getPort());
        }
        return usable;
    }

    /**
     * Detect whether a Bitcoin node is running on localhost by attempting to connect
     * to the node's port, and whether the wallet can use it as its only peer.
     */
    @VisibleForTesting
    static boolean detect(NetworkParameters params, int port) {
        try (Socket socket = new Socket()) {
            var address = new InetSocketAddress(InetAddress.getLoopbackAddress(), port);
            socket.connect(address, CONNECTION_TIMEOUT);
            log.info("Local Bitcoin node detected on port {}", port);
            return canServeWallet(params, socket);
        } catch (IOException ex) {
            log.info("No local Bitcoin node detected on port {}.", port);
            return false;
        }
    }

    // The local node is the only peer of the wallet. bitcoinj downloads blocks only from a peer
    // that serves the full block chain and witness data, and it disconnects a peer without bloom
    // filters. A pruned node announces NODE_NETWORK_LIMITED instead of NODE_NETWORK.
    private static boolean canServeWallet(NetworkParameters params, Socket socket) {
        VersionMessage versionMessage;
        try {
            versionMessage = readVersionMessage(params, socket);
        } catch (IOException | RuntimeException e) {
            log.warn("Could not read the version message of the local Bitcoin node, " +
                    "so we use it without knowing its services. {}", e.toString());
            return true;
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
            return true;
        }
        log.warn("The local Bitcoin node cannot serve the wallet: {} (services: {}). " +
                        "We do not use it and connect to the Bitcoin network instead.",
                String.join("; ", missing), VersionMessage.toStringServices(versionMessage.localServices));
        return false;
    }

    // A node answers the version message of a new peer with its own version message.
    private static VersionMessage readVersionMessage(NetworkParameters params, Socket socket) throws IOException {
        socket.setSoTimeout(CONNECTION_TIMEOUT);
        MessageSerializer serializer = params.getDefaultSerializer();
        serializer.serialize(new VersionMessage(params, 0), socket.getOutputStream());

        DataInputStream in = new DataInputStream(socket.getInputStream());
        byte[] magic = new byte[4];
        in.readFully(magic);
        if (Utils.readUint32BE(magic, 0) != params.getPacketMagic()) {
            throw new ProtocolException("The answer is not a Bitcoin message of this network");
        }
        byte[] headerBytes = new byte[BitcoinSerializer.BitcoinPacketHeader.HEADER_LENGTH];
        in.readFully(headerBytes);
        BitcoinSerializer.BitcoinPacketHeader header = serializer.deserializeHeader(ByteBuffer.wrap(headerBytes));
        if (!header.command.equals("version")) {
            throw new ProtocolException("Expected a version message, got " + header.command);
        }
        byte[] payload = new byte[header.size];
        in.readFully(payload);
        return (VersionMessage) serializer.deserializePayload(header, ByteBuffer.wrap(payload));
    }
}
