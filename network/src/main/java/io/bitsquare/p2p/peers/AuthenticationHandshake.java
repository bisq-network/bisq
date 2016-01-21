package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionPriority;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.auth.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

// Authentication protocol: 
// client: send AuthenticationRequest to seedNode
// seedNode: close connection
// seedNode: send AuthenticationChallenge to client on a new connection to test if address is correct
// client: authentication to seedNode done if nonce verification is ok
// client: AuthenticationFinalResponse to seedNode
// seedNode: authentication to client done if nonce verification is ok

public class AuthenticationHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandshake.class);

    private final NetworkNode networkNode;
    private final NodeAddress myNodeAddress;
    private final NodeAddress peerNodeAddress;
    private final Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier;
    private final BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer;

    private final long startAuthTs;
    private long nonce = 0;
    private boolean stopped;
    private Optional<SettableFuture<Connection>> resultFutureOptional = Optional.empty();
    private Timer timeoutTimer, shutDownTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AuthenticationHandshake(NetworkNode networkNode,
                                   NodeAddress myNodeAddress,
                                   NodeAddress peerNodeAddress,
                                   Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier,
                                   BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer) {
        Log.traceCall("peerAddress " + peerNodeAddress);
        this.authenticatedAndReportedPeersSupplier = authenticatedAndReportedPeersSupplier;
        this.addReportedPeersConsumer = addReportedPeersConsumer;
        this.networkNode = networkNode;
        this.myNodeAddress = myNodeAddress;
        this.peerNodeAddress = peerNodeAddress;

        startAuthTs = System.currentTimeMillis();
        networkNode.addMessageListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        // called from other thread but mapped to user thread. That can cause async behaviour.
        // Example: We got the AuthenticationHandshake shut down and the message listener 
        // has been already removed but we still get the onMessage called as the Platform.runLater get called at the next
        // cycle. So we need to protect a late call with the stopped flag.
        if (!stopped) {
            if (message instanceof AuthenticationMessage) {
                // We are listening on all connections, so we need to filter out only our peer
                if (((AuthenticationMessage) message).senderNodeAddress.equals(peerNodeAddress)) {
                    Log.traceCall(message.toString());

                    if (timeoutTimer != null)
                        timeoutTimer.cancel();

                    if (message instanceof AuthenticationChallenge) {
                        // Requesting peer
                        AuthenticationChallenge authenticationChallenge = (AuthenticationChallenge) message;
                        // We need to set the address to the connection, otherwise we will not find the connection when sending
                        // the next message and we would create a new outbound connection instead using the inbound.
                        connection.setPeerAddress(authenticationChallenge.senderNodeAddress);
                        // We use the active connectionType if we started the authentication request to another peer
                        connection.setConnectionPriority(ConnectionPriority.ACTIVE);
                        log.trace("Received authenticationChallenge from " + peerNodeAddress);
                        boolean verified = nonce != 0 && nonce == authenticationChallenge.requesterNonce;
                        if (verified) {
                            AuthenticationFinalResponse authenticationFinalResponse = new AuthenticationFinalResponse(myNodeAddress,
                                    authenticationChallenge.responderNonce,
                                    new HashSet<>(authenticatedAndReportedPeersSupplier.get()));
                            SettableFuture<Connection> future = networkNode.sendMessage(peerNodeAddress, authenticationFinalResponse);
                            log.trace("Sent AuthenticationFinalResponse {} to {}", authenticationFinalResponse, peerNodeAddress);
                            Futures.addCallback(future, new FutureCallback<Connection>() {
                                @Override
                                public void onSuccess(Connection connection) {
                                    log.trace("Successfully sent AuthenticationFinalResponse to {}", peerNodeAddress);

                                    log.info("AuthenticationComplete: Peer with address " + peerNodeAddress
                                            + " authenticated (" + connection.getUid() + "). Took "
                                            + (System.currentTimeMillis() - startAuthTs) + " ms.");
                                    completed(connection);
                                }

                                @Override
                                public void onFailure(@NotNull Throwable throwable) {
                                    log.info("AuthenticationFinalResponse sending failed " + throwable.getMessage());
                                    failed(throwable);
                                }
                            });

                            // now we add the reported peers to our list 
                            addReportedPeersConsumer.accept(authenticationChallenge.reportedPeers, connection);
                        } else {
                            // We don't call failed as it might be that we get an old authenticationChallenge from a 
                            // previously timed out request
                            // We simply ignore the authenticationChallenge if the nonce is not matching to avoid that 
                            // the current authentication turn gets terminated as well
                            log.warn("Verification of nonce failed. Maybe we got an old authenticationChallenge " +
                                    "from a timed out request" +
                                    "\nnonce={} / peerAddress={} / authenticationChallenge={}", nonce, peerNodeAddress, authenticationChallenge);
                            //failed(new AuthenticationException("Verification of nonce failed. AuthenticationChallenge=" + authenticationChallenge + " / nonceMap=" + nonce));
                        }
                    } else if (message instanceof AuthenticationFinalResponse) {
                        // Responding peer
                        AuthenticationFinalResponse authenticationFinalResponse = (AuthenticationFinalResponse) message;
                        log.trace("Received AuthenticationFinalResponse from " + peerNodeAddress + " at " + myNodeAddress);
                        boolean verified = nonce != 0 && nonce == authenticationFinalResponse.responderNonce;
                        if (verified) {
                            addReportedPeersConsumer.accept(authenticationFinalResponse.reportedPeers, connection);
                            log.info("AuthenticationComplete: Peer with address " + peerNodeAddress
                                    + " authenticated (" + connection.getUid() + "). Took "
                                    + (System.currentTimeMillis() - startAuthTs) + " ms.");
                            completed(connection);
                        } else {
                            // We don't call failed as it might be that we get an old authenticationFinalResponse from a 
                            // previously timed out request
                            // We simply ignore the authenticationFinalResponse if the nonce is not matching to avoid that 
                            // the current authentication turn gets terminated as well
                            log.warn("Verification of nonce failed. Maybe we got an old authenticationFinalResponse " +
                                    "from a timed out request" +
                                    "\nnonce={} / peerAddress={} / authenticationChallenge={}", nonce, peerNodeAddress, authenticationFinalResponse);
                            log.warn("Verification of nonce failed. nonce={} / peerAddress={} / authenticationFinalResponse={}", nonce, peerNodeAddress, authenticationFinalResponse);
                            //failed(new AuthenticationException("Verification of nonce failed. getPeersMessage=" + authenticationFinalResponse + " / nonce=" + nonce));
                        }
                    } else if (message instanceof AuthenticationRejection) {
                        // Any peer
                        failed(new AuthenticationException("Authentication to peer "
                                + ((AuthenticationRejection) message).senderNodeAddress
                                + " rejected because of a race conditions."));
                    }
                }
            }
        } else {
            // TODO leave that for debugging for now, but remove it once the network is tested sufficiently
            log.info("AuthenticationHandshake (peerAddress={}) already shut down but still got onMessage called. " +
                    "That can happen because of Thread mapping.", peerNodeAddress);
            log.debug("message={}", message);
            log.debug("connection={}", connection);
            return;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication initiated by requesting peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<Connection> requestAuthentication() {
        Log.traceCall("peerAddress " + peerNodeAddress);
        // Requesting peer

        if (stopped) {
            // TODO leave that for debugging for now, but remove it once the network is tested sufficiently
            log.warn("AuthenticationHandshake (peerAddress={}) already shut down but still got requestAuthentication called. That must not happen.", peerNodeAddress);
        }

        resultFutureOptional = Optional.of(SettableFuture.create());
        AuthenticationRequest authenticationRequest = new AuthenticationRequest(myNodeAddress, getAndSetNonce());
        SettableFuture<Connection> future = networkNode.sendMessage(peerNodeAddress, authenticationRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("send AuthenticationRequest to " + peerNodeAddress + " succeeded.");

                // We protect that connection from getting closed by maintenance cleanup...
                connection.setConnectionPriority(ConnectionPriority.AUTH_REQUEST);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send AuthenticationRequest to " + peerNodeAddress + " failed. " +
                        "It might be that the peer went offline.\nException:" + throwable.getMessage());
                failed(throwable);
            }
        });

        if (timeoutTimer != null)
            timeoutTimer.cancel();

        timeoutTimer = UserThread.runAfter(() -> failed(new AuthenticationException("Authentication to peer "
                + peerNodeAddress
                + " failed because of a timeout. " +
                "We did not get an AuthenticationChallenge message responded after 30 sec.")), 30);

        return resultFutureOptional.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Responding to authentication request
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<Connection> respondToAuthenticationRequest(AuthenticationRequest authenticationRequest,
                                                                     Connection connection) {
        Log.traceCall("peerAddress " + peerNodeAddress);
        // Responding peer

        if (stopped) {
            // TODO leave that for debugging for now, but remove it once the network is tested sufficiently
            log.warn("AuthenticationHandshake (peerAddress={}) already shut down but still got respondToAuthenticationRequest called. That must not happen.", peerNodeAddress);
            log.warn("authenticationRequest={}", authenticationRequest);
            log.warn("connection={}", connection);
        }

        resultFutureOptional = Optional.of(SettableFuture.create());

        log.info("We shut down inbound connection from peer {} to establish a new " +
                "connection with his reported address to verify if his address is correct.", peerNodeAddress);

        connection.shutDown(() -> {
            if (shutDownTimer != null)
                shutDownTimer.cancel();

            shutDownTimer = UserThread.runAfter(() -> {
                if (!stopped) {
                    // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                    // inconsistent state
                    log.trace("respondToAuthenticationRequest: connection.shutDown complete. peerAddress=" + peerNodeAddress + " / myAddress=" + myNodeAddress);

                    // we send additionally the reported and authenticated peers to save one message in the protocol.
                    AuthenticationChallenge authenticationChallenge = new AuthenticationChallenge(myNodeAddress,
                            authenticationRequest.requesterNonce,
                            getAndSetNonce(),
                            new HashSet<>(authenticatedAndReportedPeersSupplier.get()));
                    SettableFuture<Connection> future = networkNode.sendMessage(peerNodeAddress, authenticationChallenge);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("AuthenticationChallenge successfully sent");

                            // We use passive connectionType for connections created from received authentication 
                            // requests from other peers 
                            connection.setConnectionPriority(ConnectionPriority.PASSIVE);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.warn("Failure at sending AuthenticationChallenge to {}. It might be that the peer went offline. Exception={}", peerNodeAddress, throwable.getMessage());
                            failed(throwable);
                        }
                    });

                    if (timeoutTimer != null)
                        timeoutTimer.cancel();

                    timeoutTimer = UserThread.runAfter(() -> failed(new AuthenticationException("Authentication of peer "
                            + peerNodeAddress
                            + " failed because of a timeout. " +
                            "We did not get an AuthenticationFinalResponse message responded after 30 sec.\n" +
                            "")), 30, TimeUnit.SECONDS);

                } else {
                    log.info("AuthenticationHandshake (peerAddress={}) already shut down before we could sent " +
                            "AuthenticationChallenge. That might happen in rare cases.", peerNodeAddress);
                }
            }, 2000, TimeUnit.MILLISECONDS); // Don't set the delay too short as the CloseConnectionMessage might arrive too late at the peer
        });
        return resultFutureOptional.get();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cancel 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cancel(NodeAddress peerNodeAddress) {
        Log.traceCall();
        failed(new AuthenticationException("Authentication to peer "
                + peerNodeAddress
                + " canceled because of a race conditions."));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getter
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Optional<SettableFuture<Connection>> getResultFutureOptional() {
        return resultFutureOptional;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long getAndSetNonce() {
        Log.traceCall();
        nonce = new Random().nextLong();
        while (nonce == 0)
            nonce = new Random().nextLong();

        return nonce;
    }

    private void failed(@NotNull Throwable throwable) {
        Log.traceCall(throwable.toString());
        shutDown();
        if (resultFutureOptional.isPresent())
            resultFutureOptional.get().setException(throwable);
        else
            log.warn("failed called but resultFuture = null. That must never happen.");
    }

    private void completed(Connection connection) {
        Log.traceCall();
        shutDown();
        if (resultFutureOptional.isPresent())
            resultFutureOptional.get().set(connection);
        else
            log.warn("completed called but resultFuture = null. That must never happen.");
    }

    private void shutDown() {
        Log.traceCall("peerAddress = " + peerNodeAddress);
        stopped = true;

        if (timeoutTimer != null)
            timeoutTimer.cancel();

        if (shutDownTimer != null)
            shutDownTimer.cancel();

        networkNode.removeMessageListener(this);
    }
}
