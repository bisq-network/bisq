package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.ConnectionPriority;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.auth.*;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

// authentication protocol: 
// node2 -> node1 AuthenticationRequest
// node1: close connection
// node1 -> node2 AuthenticationResponse on new connection
// node2: authentication to node1 done if nonce ok
// node2 -> node1 GetPeersAuthRequest
// node1: authentication to node2 done if nonce ok
// node1 -> node2 GetPeersAuthResponse

public class AuthenticationHandshake implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(AuthenticationHandshake.class);

    private final NetworkNode networkNode;
    private final PeerGroup peerGroup;
    private final Address myAddress;
    private final Address peerAddress;

    private SettableFuture<Connection> resultFuture;
    private long startAuthTs;
    private long nonce = 0;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AuthenticationHandshake(NetworkNode networkNode, PeerGroup peerGroup, Address myAddress, Address peerAddress) {
        Log.traceCall("peerAddress " + peerAddress);
        this.networkNode = networkNode;
        this.peerGroup = peerGroup;
        this.myAddress = myAddress;
        this.peerAddress = peerAddress;

        networkNode.addMessageListener(this);
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof AuthenticationMessage) {
            // We are listening on all connections, so we need to filter out only our peer address
            if (((AuthenticationMessage) message).address.equals(peerAddress)) {
                Log.traceCall(message.toString());
                checkArgument(!stopped);
                if (message instanceof AuthenticationResponse) {
                    // Requesting peer
                    // We use the active connectionType if we started the authentication request to another peer
                    // That is used for protecting eclipse attacks
                    connection.setConnectionPriority(ConnectionPriority.ACTIVE);

                    AuthenticationResponse authenticationResponse = (AuthenticationResponse) message;
                    connection.setPeerAddress(peerAddress);
                    log.trace("Received authenticationResponse from " + peerAddress);
                    boolean verified = nonce != 0 && nonce == authenticationResponse.requesterNonce;
                    if (verified) {
                        GetPeersAuthRequest getPeersAuthRequest = new GetPeersAuthRequest(myAddress,
                                authenticationResponse.responderNonce,
                                new HashSet<>(peerGroup.getAuthenticatedAndReportedPeers()));
                        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, getPeersAuthRequest);
                        log.trace("Sent GetPeersAuthRequest {} to {}", getPeersAuthRequest, peerAddress);
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("Successfully sent GetPeersAuthRequest {} to {}", getPeersAuthRequest, peerAddress);
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("GetPeersAuthRequest sending failed " + throwable.getMessage());
                                failed(throwable);
                            }
                        });

                        // We could set already the authenticated flag here already, but as we need the reported peers we need
                        // to wait for the GetPeersAuthResponse before we are completed.
                    } else {
                        log.warn("verify nonce failed. AuthenticationResponse=" + authenticationResponse + " / nonce=" + nonce);
                        failed(new Exception("Verify nonce failed. AuthenticationResponse=" + authenticationResponse + " / nonceMap=" + nonce));
                    }
                } else if (message instanceof GetPeersAuthRequest) {
                    // Responding peer
                    GetPeersAuthRequest getPeersAuthRequest = (GetPeersAuthRequest) message;
                    log.trace("GetPeersAuthRequest from " + peerAddress + " at " + myAddress);
                    boolean verified = nonce != 0 && nonce == getPeersAuthRequest.responderNonce;
                    if (verified) {
                        // we create the msg with our already collected peer addresses (before adding the new ones)
                        GetPeersAuthResponse getPeersAuthResponse = new GetPeersAuthResponse(myAddress,
                                new HashSet<>(peerGroup.getAuthenticatedAndReportedPeers()));
                        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, getPeersAuthResponse);
                        log.trace("Sent GetPeersAuthResponse {} to {}", getPeersAuthResponse, peerAddress);

                        // now we add the reported peers to our own set
                        HashSet<ReportedPeer> reportedPeers = getPeersAuthRequest.reportedPeers;
                        log.trace("Received reported peers: " + reportedPeers);
                        peerGroup.addToReportedPeers(reportedPeers, connection);

                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("Successfully sent GetPeersAuthResponse {} to {}", getPeersAuthResponse, peerAddress);
                                log.info("AuthenticationComplete: Peer with address " + peerAddress
                                        + " authenticated (" + connection.getUid() + "). Took "
                                        + (System.currentTimeMillis() - startAuthTs) + " ms.");

                                completed(connection);
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("GetPeersAuthResponse sending failed " + throwable.getMessage());
                                failed(throwable);
                            }
                        });
                    } else {
                        log.warn("verify nonce failed. getPeersMessage=" + getPeersAuthRequest + " / nonce=" + nonce);
                        failed(new Exception("Verify nonce failed. getPeersMessage=" + getPeersAuthRequest + " / nonce=" + nonce));
                    }
                } else if (message instanceof GetPeersAuthResponse) {
                    // Requesting peer
                    GetPeersAuthResponse getPeersAuthResponse = (GetPeersAuthResponse) message;
                    log.trace("GetPeersAuthResponse from " + peerAddress + " at " + myAddress);
                    HashSet<ReportedPeer> reportedPeers = getPeersAuthResponse.reportedPeers;
                    log.trace("Received reported peers: " + reportedPeers);
                    peerGroup.addToReportedPeers(reportedPeers, connection);

                    log.info("AuthenticationComplete: Peer with address " + peerAddress
                            + " authenticated (" + connection.getUid() + "). Took "
                            + (System.currentTimeMillis() - startAuthTs) + " ms.");

                    completed(connection);
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Authentication initiated by requesting peer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<Connection> requestAuthentication() {
        Log.traceCall("peerAddress " + peerAddress);
        // Requesting peer

        AuthenticationRequest authenticationRequest = new AuthenticationRequest(myAddress, getAndSetNonce());
        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, authenticationRequest);
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(Connection connection) {
                log.trace("send AuthenticationRequest to " + peerAddress + " succeeded.");

                connection.setPeerAddress(peerAddress);
                // We protect that connection from getting closed by maintenance cleanup...
                connection.setConnectionPriority(ConnectionPriority.AUTH_REQUEST);
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send AuthenticationRequest to " + peerAddress + " failed." +
                        "\nException:" + throwable.getMessage());
                failed(throwable);
            }
        });

        return resultFuture;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Responding to authentication request
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<Connection> respondToAuthenticationRequest(AuthenticationRequest authenticationRequest,
                                                                     Connection connection) {
        Log.traceCall("peerAddress " + peerAddress);
        // Responding peer

        log.trace("AuthenticationRequest from " + peerAddress + " at " + myAddress);
        log.info("We shut down inbound connection from peer {} to establish a new " +
                "connection with his reported address.", peerAddress);

        connection.shutDown(() -> {
            UserThread.runAfter(() -> {
                if (!stopped) {
                    // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                    // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                    log.trace("processAuthenticationMessage: connection.shutDown complete. AuthenticationRequest from " + peerAddress + " at " + myAddress);

                    AuthenticationResponse authenticationResponse = new AuthenticationResponse(myAddress,
                            authenticationRequest.requesterNonce,
                            getAndSetNonce());
                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, authenticationResponse);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(@Nullable Connection connection) {
                            log.trace("onSuccess sending AuthenticationResponse");

                            connection.setPeerAddress(peerAddress);
                            // We use passive connectionType for connections created from received authentication requests from other peers 
                            // That is used for protecting eclipse attacks
                            connection.setConnectionPriority(ConnectionPriority.PASSIVE);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.warn("onFailure sending AuthenticationResponse.");
                            failed(throwable);
                        }
                    });
                }
            }, 200, TimeUnit.MILLISECONDS);
        });
        return resultFuture;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Cancel if we send reject message
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void cancel() {
        failed(new CancelAuthenticationException());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private long getAndSetNonce() {
        Log.traceCall();
        nonce = new Random().nextLong();
        while (nonce == 0)
            nonce = getAndSetNonce();

        return nonce;
    }

    private void failed(@NotNull Throwable throwable) {
        Log.traceCall();
        shutDown();
        resultFuture.setException(throwable);
    }

    private void completed(Connection connection) {
        Log.traceCall();
        shutDown();
        resultFuture.set(connection);
    }

    private void shutDown() {
        Log.traceCall();
        networkNode.removeMessageListener(this);
        stopped = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthenticationHandshake)) return false;

        AuthenticationHandshake that = (AuthenticationHandshake) o;

        return !(peerAddress != null ? !peerAddress.equals(that.peerAddress) : that.peerAddress != null);

    }

    @Override
    public int hashCode() {
        return peerAddress != null ? peerAddress.hashCode() : 0;
    }

}
