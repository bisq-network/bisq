package bisq.core.btc.nodes;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerAddress;
import org.bitcoinj.core.VersionMessage;
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener;
import org.bitcoinj.net.NioClient;
import org.bitcoinj.params.MainNetParams;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;

import java.io.IOException;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Detects whether a Bitcoin node is running on localhost and whether it is well configured
 * (meaning it's not pruning and has bloom filters enabled). The class is split into
 * methods that perform relevant checks and cache the result (methods that start with "check"),
 * and methods that query that cache (methods that start with "is"). The querying methods return
 * an Optional<Boolean>, whose emptiness shows if the relevant check is pending, and whose
 * contents show the result of the check. Method/s starting with "safeIs" are provided to be
 * used where the calling code was not refactored to handle Optionals (see
 * {@code LocalBitcoinNode#handleUnsafeQuery}).
 * @see bisq.common.config.Config#ignoreLocalBtcNode
 */
@Singleton
public class LocalBitcoinNode {

    public static final String LOCAL_BITCOIN_NODE_PORT = "localBitcoinNodePort";

    private static final Logger log = LoggerFactory.getLogger(LocalBitcoinNode.class);
    private static final int CONNECTION_TIMEOUT = 5000;

    private final int port;
    private Optional<Boolean> detected = Optional.empty();
    private Optional<Boolean> wellConfigured = Optional.empty();

    @Inject
    public LocalBitcoinNode(@Named(LOCAL_BITCOIN_NODE_PORT) int port) {
        this.port = port;
    }

    /**
     * Creates an NioClient that is expected to only be used to coerce a VersionMessage out of a
     * local Bitcoin node and be closed right after.
     */

    private static NioClient createClient(
            Peer peer, int port, int connectionTimeout
    ) throws IOException {
        InetSocketAddress serverAddress =
                new InetSocketAddress(InetAddress.getLocalHost(), port);

        // This initiates the handshake procedure, which, if successful, will complete
        // the peerVersionMessageFuture, or be cancelled, in case of failure.
        NioClient client = new NioClient(serverAddress, peer, connectionTimeout);

        return client;
    }

    /**
     * Creates a Peer that is expected to only be used to coerce a VersionMessage out of a
     * local Bitcoin node and be closed right after.
     */

    private static Peer createLocalPeer(
            int port
    ) throws UnknownHostException {
        // TODO: what's the effect of NetworkParameters on handshake?
        // i.e. is it fine to just always use MainNetParams?
        var networkParameters = new MainNetParams();

        // We must construct a BitcoinJ Context before using BitcoinJ.
        // We don't keep a reference, because it's automatically kept in a thread local storage.
        new Context(networkParameters);

        var ourVersionMessage = new VersionMessage(networkParameters, 0);

        var localPeerAddress = new PeerAddress(InetAddress.getLocalHost(), port);

        AbstractBlockChain blockchain = null;

        var peer = new Peer(networkParameters, ourVersionMessage, localPeerAddress, blockchain);
        return peer;
    }

    private static boolean checkWellConfigured(VersionMessage versionMessage) {
        var notPruning = versionMessage.hasBlockChain();
        var supportsAndAllowsBloomFilters =
                isBloomFilteringSupportedAndEnabled(versionMessage);
        return notPruning && supportsAndAllowsBloomFilters;
    }

    /**
     * Method backported from upstream bitcoinj: at the time of writing, our version is
     * not BIP111-aware.
     * Source routines and data can be found in Bitcoinj under:
     * core/src/main/java/org/bitcoinj/core/VersionMessage.java
     * and
     * core/src/main/java/org/bitcoinj/core/NetworkParameters.java
     */

    private static boolean isBloomFilteringSupportedAndEnabled(VersionMessage versionMessage) {
        // A service bit that denotes whether the peer supports BIP37 bloom filters or not.
        // The service bit is defined in BIP111.
        int NODE_BLOOM = 1 << 2;

        int BLOOM_FILTERS_BIP37_PROTOCOL_VERSION = 70000;
        var whenBloomFiltersWereIntroduced = BLOOM_FILTERS_BIP37_PROTOCOL_VERSION;

        int BLOOM_FILTERS_BIP111_PROTOCOL_VERSION = 70011;
        var whenBloomFiltersWereDisabledByDefault = BLOOM_FILTERS_BIP111_PROTOCOL_VERSION;

        int clientVersion = versionMessage.clientVersion;
        long localServices = versionMessage.localServices;

        if (clientVersion >= whenBloomFiltersWereIntroduced
                && clientVersion < whenBloomFiltersWereDisabledByDefault)
            return true;
        return (localServices & NODE_BLOOM) == NODE_BLOOM;
    }

    /**
     * Initiates detection and configuration checks. The results are cached so that the public
     * methods isUsable, isDetected, isWellConfigured don't trigger a recheck.
     */

    public boolean checkUsable() {
        var optionalVersionMessage = attemptHandshakeForVersionMessage();
        handleHandshakeAttempt(optionalVersionMessage);
        // We know that the Optional/s will be populated by the end of the checks.
        return isUsable().get();
    }

    private void handleHandshakeAttempt(Optional<VersionMessage> optionalVersionMessage) {
        if (!optionalVersionMessage.isPresent()) {
            detected = Optional.of(false);
            wellConfigured = Optional.of(false);
            log.info("No local Bitcoin node detected on port {},"
                    + " or the connection was prematurely closed"
                    + " (before a version messages could be coerced)",
                    port);
        } else {
            detected = Optional.of(true);
            log.info("Local Bitcoin node detected on port {}", port);

            var versionMessage = optionalVersionMessage.get();
            var configurationCheckResult = checkWellConfigured(versionMessage);

            if (configurationCheckResult) {
                wellConfigured = Optional.of(true);
                log.info("Local Bitcoin node found to be well configured"
                        + " (not pruning and allows bloom filters)");
            } else {
                wellConfigured = Optional.of(false);
                log.info("Local Bitcoin node badly configured"
                        + " (it is pruning and/or bloom filters are disabled)");
            }
        }
    }

    /** Performs a blocking Bitcoin protocol handshake, which includes exchanging version messages and acks.
     *  Its purpose is to check if a local Bitcoin node is running, and, if it is, check its advertised
     *  configuration. The returned Optional is empty, if a local peer wasn't found, or if handshake failed
     *  for some reason.  This method could be noticably simplified, by turning connection failure callback
     *  into a future and using a first-future-to-complete type of construct, but I couldn't find a
     *  ready-made implementation.
     */

    private Optional<VersionMessage> attemptHandshakeForVersionMessage() {
        Peer peer;
        try {
            peer = createLocalPeer(port);
        } catch (UnknownHostException ex) {
            log.error("Local bitcoin node handshake attempt unexpectedly threw: {}", ex);
            return Optional.empty();
        }

        NioClient client;
        try {
            log.info("Initiating attempt to connect to and handshake with a local "
                    + "Bitcoin node (which may or may not be running) on port {}.",
                    port);
            client = createClient(peer, port, CONNECTION_TIMEOUT);
        } catch (IOException ex) {
            log.error("Local bitcoin node handshake attempt unexpectedly threw: {}", ex);
            return Optional.empty();
        }

        ListenableFuture<VersionMessage> peerVersionMessageFuture = getVersionMessage(peer);

        Optional<VersionMessage> optionalPeerVersionMessage;

        // block for VersionMessage or cancellation (in case of connection failure)
        try {
            var peerVersionMessage = peerVersionMessageFuture.get();
            optionalPeerVersionMessage = Optional.of(peerVersionMessage);
        } catch (ExecutionException | InterruptedException | CancellationException ex) {
            optionalPeerVersionMessage = Optional.empty();
        }

        peer.close();
        client.closeConnection();

        return optionalPeerVersionMessage;
    }

    private ListenableFuture<VersionMessage> getVersionMessage(Peer peer) {
        SettableFuture<VersionMessage> peerVersionMessageFuture = SettableFuture.create();

        var versionHandshakeDone = peer.getVersionHandshakeFuture();
        FutureCallback<Peer> fetchPeerVersionMessage = new FutureCallback<Peer>() {
            public void onSuccess(Peer peer) {
                peerVersionMessageFuture.set(peer.getPeerVersionMessage());
            }
            public void onFailure(Throwable thr) {
                // No action
            }
        };
        Futures.addCallback(
                versionHandshakeDone,
                fetchPeerVersionMessage
        );

        PeerDisconnectedEventListener cancelIfConnectionFails =
                new PeerDisconnectedEventListener() {
                    public void onPeerDisconnected(Peer peer, int peerCount) {
                        var peerVersionMessageAlreadyReceived =
                                peerVersionMessageFuture.isDone();
                        if (peerVersionMessageAlreadyReceived) {
                            // This method is called whether or not the handshake was successful.
                            // In case it was successful, we don't want to do anything here.
                            return;
                        }
                        // In some cases Peer will self-disconnect after receiving
                        // node's VersionMessage, but before completing the handshake.
                        // In such a case, we want to retrieve the VersionMessage.
                        var peerVersionMessage = peer.getPeerVersionMessage();
                        if (peerVersionMessage != null) {
                            log.info("Handshake attempt was interrupted;"
                                    + " however, the local node's version message was coerced.");
                            peerVersionMessageFuture.set(peerVersionMessage);
                        } else {
                            log.info("Handshake attempt did not result in a version message exchange.");
                            var mayInterruptWhileRunning = true;
                            peerVersionMessageFuture.cancel(mayInterruptWhileRunning);
                        }
                    }
                };

        // Cancel peerVersionMessageFuture if connection failed
        peer.addDisconnectedEventListener(cancelIfConnectionFails);

        return peerVersionMessageFuture;
    }

    /**
     * Returns an optional that, in case it is not empty, shows whether or not the
     * local node was fit for usage at the time the checks were performed called,
     * meaning it's been detected and its configuration satisfied our checks; or, in
     * the case that it is empty, it signifies that the checks have not yet completed.
     */

    public Optional<Boolean> isUsable() {
        // If a node is found to be well configured, it implies that it was also detected,
        // so this is query is enough to show if the relevant checks were performed and if
        // their results are positive.
        return isWellConfigured();
    }

    /**
     * Returns an Optional<Boolean> that, when not empty, tells you whether the local node
     * was detected, but misconfigured.
     */

    public Optional<Boolean> isDetectedButMisconfigured() {
        return isDetected().flatMap(goodDetect ->
                isWellConfigured().map(goodConfig ->
                        goodDetect && !goodConfig
                ));
    }

    /**
     * Returns an optional, which is empty in case detection has not yet completed, or
     * which contains a Boolean, in case detection has been performed, which signifies
     * whether or not a Bitcoin node was running on localhost at the time the checks were
     * performed. No further monitoring is performed, so if the node goes up or down in the
     * meantime, this method will continue to return its original value. See
     * {@code MainViewModel#setupBtcNumPeersWatcher} to understand how disconnection and
     * reconnection of the local Bitcoin node is actually handled.
     */
    public Optional<Boolean> isDetected() {
        return detected;
    }

    /**
     * Returns an optional whose emptiness signifies whether or not configuration checks
     * have been performed, and its Boolean contents whether the local node's configuration
     * satisfied our checks at the time they were performed. We check if the local node is
     * not pruning and has bloom filters enabled.
     */

    public Optional<Boolean> isWellConfigured() {
        return wellConfigured;
    }

    /**
     * A "safe" variant, which, in case LocalBitcoinNode checks were
     * not performed, reverts to legacy behaviour and logs an error message. See
     * {@code LocalBitcoinNode#handleUnsafeQuery}.
     */
    public boolean safeIsUsable() {
        return handleUnsafeQuery(isUsable());
    }

    private boolean handleUnsafeQuery(Optional<Boolean> opt) {
        return opt.orElseGet(() -> {
            /* Returning false when checks haven't been performed yet is what the behaviour
             * was before we switched to using Optionals. More specifically, the only query
             * method at the time, isDetected(), would return false in such a case. We are
             * relatively confident that the previous behaviour doesn't cause fatal bugs,
             * so, in case LocalBitcoinNode is queried too early, we revert to it, instead
             * of letting Optional.empty().get() throw an exception. The advantage over
             * plain booleans then is that we can log the below error message (with
             * stacktrace).
             */
            var whenChecksNotFinished = false;

            var throwable = new Throwable("LocalBitcoinNode was queried before it was ready");

            log.error("Unexpectedly queried LocalBitcoinNode before its checks were performed."
                    + " This should never happen."
                    + " Please report this on Bisq's issue tracker, including the following stacktrace:",
                    throwable);

            return whenChecksNotFinished;
        });
    }
}
