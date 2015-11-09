package io.bitsquare.p2p.peers;

import com.google.common.collect.Sets;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.util.Tuple2;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.auth.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;


// authentication example: 
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

    private SettableFuture<Connection> resultFuture;
    private long startAuthTs;
    private long nonce = 0;
    private boolean stopped;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AuthenticationHandshake(NetworkNode networkNode, PeerGroup peerGroup, Address myAddress) {
        Log.traceCall();
        this.networkNode = networkNode;
        this.peerGroup = peerGroup;
        this.myAddress = myAddress;

        networkNode.addMessageListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        Log.traceCall();
        if (message instanceof AuthenticationMessage) {
            if (message instanceof AuthenticationResponse) {
                // Requesting peer
                AuthenticationResponse authenticationResponse = (AuthenticationResponse) message;
                Address peerAddress = authenticationResponse.address;
                log.trace("ChallengeMessage from " + peerAddress + " at " + myAddress);
                boolean verified = nonce != 0 && nonce == authenticationResponse.requesterNonce;
                if (verified) {
                    connection.setPeerAddress(peerAddress);
                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                            new GetPeersAuthRequest(myAddress, authenticationResponse.challengerNonce, new HashSet<>(peerGroup.getAllPeerAddresses())));
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("GetPeersAuthRequest sent successfully from " + myAddress + " to " + peerAddress);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("GetPeersAuthRequest sending failed " + throwable.getMessage());
                            onFault(throwable);
                        }
                    });
                } else {
                    log.warn("verify nonce failed. challengeMessage=" + authenticationResponse + " / nonce=" + nonce);
                    onFault(new Exception("Verify nonce failed. challengeMessage=" + authenticationResponse + " / nonceMap=" + nonce));
                }
            } else if (message instanceof GetPeersAuthRequest) {
                // Responding peer
                GetPeersAuthRequest getPeersAuthRequest = (GetPeersAuthRequest) message;
                Address peerAddress = getPeersAuthRequest.address;
                log.trace("GetPeersMessage from " + peerAddress + " at " + myAddress);
                boolean verified = nonce != 0 && nonce == getPeersAuthRequest.challengerNonce;
                if (verified) {
                    // we add the reported peers to our own set
                    HashSet<Address> peerAddresses = getPeersAuthRequest.peerAddresses;
                    log.trace("Received peers: " + peerAddresses);
                    peerGroup.addToReportedPeers(peerAddresses, connection);

                    SettableFuture<Connection> future = networkNode.sendMessage(peerAddress,
                            new GetPeersAuthResponse(myAddress, new HashSet<>(peerGroup.getAllPeerAddresses())));
                    log.trace("sent GetPeersAuthResponse to " + peerAddress + " from " + myAddress
                            + " with allPeers=" + peerGroup.getAllPeerAddresses());
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(Connection connection) {
                            log.trace("GetPeersAuthResponse sent successfully from " + myAddress + " to " + peerAddress);
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("GetPeersAuthResponse sending failed " + throwable.getMessage());
                            onFault(throwable);
                        }
                    });

                    log.info("AuthenticationComplete: Peer with address " + peerAddress
                            + " authenticated (" + connection.objectId + "). Took "
                            + (System.currentTimeMillis() - startAuthTs) + " ms.");

                    onSuccess(connection);
                } else {
                    log.warn("verify nonce failed. getPeersMessage=" + getPeersAuthRequest + " / nonce=" + nonce);
                    onFault(new Exception("Verify nonce failed. getPeersMessage=" + getPeersAuthRequest + " / nonce=" + nonce));
                }
            } else if (message instanceof GetPeersAuthResponse) {
                // Requesting peer
                GetPeersAuthResponse getPeersAuthResponse = (GetPeersAuthResponse) message;
                Address peerAddress = getPeersAuthResponse.address;
                log.trace("GetPeersAuthResponse from " + peerAddress + " at " + myAddress);
                HashSet<Address> peerAddresses = getPeersAuthResponse.peerAddresses;
                log.trace("Received peers: " + peerAddresses);
                peerGroup.addToReportedPeers(peerAddresses, connection);

                // we wait until the handshake is completed before setting the authenticate flag
                // authentication at both sides of the connection
                log.info("AuthenticationComplete: Peer with address " + peerAddress
                        + " authenticated (" + connection.objectId + "). Took "
                        + (System.currentTimeMillis() - startAuthTs) + " ms.");

                onSuccess(connection);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public SettableFuture<Connection> requestAuthenticationToPeer(Address peerAddress) {
        Log.traceCall();
        // Requesting peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();
        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new AuthenticationRequest(myAddress, getAndSetNonce()));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.trace("send RequestAuthenticationMessage to " + peerAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send RequestAuthenticationMessage to " + peerAddress + " failed." +
                        "\nException:" + throwable.getMessage());
                onFault(throwable);
            }
        });

        return resultFuture;
    }

    public SettableFuture<Connection> requestAuthentication(Set<Address> remainingAddresses, Address peerAddress) {
        Log.traceCall(peerAddress.getFullAddress());
        // Requesting peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();
        remainingAddresses.remove(peerAddress);
        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new AuthenticationRequest(myAddress, getAndSetNonce()));
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.trace("send RequestAuthenticationMessage to " + peerAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.info("Send RequestAuthenticationMessage to " + peerAddress + " failed." +
                        "\nThat is expected if seed nodes are offline." +
                        "\nException:" + throwable.getMessage());
                onFault(throwable);
                // log.trace("We try to authenticate to another random seed nodes of that list: " + remainingAddresses);
                // authenticateToNextRandomPeer(remainingAddresses);
            }
        });

        return resultFuture;
    }

    public SettableFuture<Connection> processAuthenticationRequest(AuthenticationRequest authenticationRequest, Connection connection) {
        Log.traceCall();
        // Responding peer
        resultFuture = SettableFuture.create();
        startAuthTs = System.currentTimeMillis();

        Address peerAddress = authenticationRequest.address;
        log.trace("RequestAuthenticationMessage from " + peerAddress + " at " + myAddress);
        log.info("We shut down inbound connection from peer {} to establish a new " +
                "connection with his reported address.", peerAddress);

        //TODO check if causes problems without delay
        connection.shutDown(() -> {
            Log.traceCall();
            if (!stopped) {
                // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                log.trace("processAuthenticationMessage: connection.shutDown complete. RequestAuthenticationMessage from " + peerAddress + " at " + myAddress);

                SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new AuthenticationResponse(myAddress, authenticationRequest.nonce, getAndSetNonce()));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("onSuccess sending ChallengeMessage");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.warn("onFailure sending ChallengeMessage.");
                        onFault(throwable);
                    }
                });
            }
        });
       /* connection.shutDown(() -> UserThread.runAfter(() -> { Log.traceCall();
                    if (!stopped) {
                        // we delay a bit as listeners for connection.onDisconnect are on other threads and might lead to 
                        // inconsistent state (removal of connection from NetworkNode.authenticatedConnections)
                        log.trace("processAuthenticationMessage: connection.shutDown complete. RequestAuthenticationMessage from " + peerAddress + " at " + myAddress);

                        SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, new AuthenticationResponse(myAddress, authenticationRequest.nonce, getAndSetNonce()));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) { Log.traceCall();
                                log.trace("onSuccess sending ChallengeMessage");
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) { Log.traceCall();
                                log.warn("onFailure sending ChallengeMessage.");
                                onFault(throwable);
                            }
                        });
                    }
                },
                100 + PeerGroup.simulateAuthTorNode,
                TimeUnit.MILLISECONDS));*/

        return resultFuture;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

  /*  private void authenticateToNextRandomPeer(Set<Address> remainingAddresses) {
        Log.traceCall();
        Optional<Tuple2<Address, Set<Address>>> tupleOptional = getRandomAddressAndRemainingSet(remainingAddresses);
        if (tupleOptional.isPresent()) {
            Tuple2<Address, Set<Address>> tuple = tupleOptional.get();
            requestAuthentication(tuple.second, tuple.first);
        } else {
            log.info("No other seed node found. That is expected for the first seed node.");
            onSuccess(null);
        }
    }*/

    private Optional<Tuple2<Address, Set<Address>>> getRandomAddressAndRemainingSet(Set<Address> addresses) {
        Log.traceCall();
        if (!addresses.isEmpty()) {
            List<Address> list = new ArrayList<>(addresses);
            Collections.shuffle(list);
            Address address = list.remove(0);
            return Optional.of(new Tuple2<>(address, Sets.newHashSet(list)));
        } else {
            return Optional.empty();
        }
    }

    private long getAndSetNonce() {
        Log.traceCall();
        nonce = new Random().nextLong();
        while (nonce == 0)
            nonce = getAndSetNonce();

        return nonce;
    }

    private void onFault(@NotNull Throwable throwable) {
        Log.traceCall();
        cleanup();
        resultFuture.setException(throwable);
    }

    private void onSuccess(Connection connection) {
        Log.traceCall();
        cleanup();
        resultFuture.set(connection);
    }

    private void cleanup() {
        Log.traceCall();
        stopped = true;
        networkNode.removeMessageListener(this);
    }
}
