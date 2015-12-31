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
import io.bitsquare.p2p.peers.messages.data.DataRequest;
import io.bitsquare.p2p.peers.messages.data.DataResponse;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.data.ProtectedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;

public class RequestDataManager implements MessageListener, AuthenticationListener {
    private static final Logger log = LoggerFactory.getLogger(RequestDataManager.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listener
    ///////////////////////////////////////////////////////////////////////////////////////////

    public interface Listener {
        void onNoSeedNodeAvailable();

        void onDataReceived(Address seedNode);
    }


    private final NetworkNode networkNode;
    private final P2PDataStorage dataStorage;
    private final PeerManager peerManager;
    private final Listener listener;

    private Optional<Address> optionalConnectedSeedNodeAddress = Optional.empty();
    private Optional<Collection<Address>> optionalSeedNodeAddresses = Optional.empty();
    private boolean isSeedNode;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, PeerManager peerManager, Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.peerManager = peerManager;
        this.listener = listener;

        networkNode.addMessageListener(this);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setIsSeedNode(boolean isSeedNode) {
        this.isSeedNode = isSeedNode;
    }

    public void requestData(Collection<Address> seedNodeAddresses) {
        if (!optionalSeedNodeAddresses.isPresent())
            optionalSeedNodeAddresses = Optional.of(seedNodeAddresses);

        Log.traceCall(seedNodeAddresses.toString());
        if (!seedNodeAddresses.isEmpty()) {
            List<Address> remainingSeedNodeAddresses = new ArrayList<>(seedNodeAddresses);
            Collections.shuffle(remainingSeedNodeAddresses);
            Address candidate = remainingSeedNodeAddresses.get(0);
            if (!peerManager.isInAuthenticationProcess(candidate)) {
                // We only remove it if it is not in the process of authentication
                remainingSeedNodeAddresses.remove(0);
                log.info("We try to send a GetAllDataMessage request to a random seed node. " + candidate);

                SettableFuture<Connection> future = networkNode.sendMessage(candidate, new DataRequest());
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(@Nullable Connection connection) {
                        log.info("Send GetAllDataMessage to " + candidate + " succeeded.");
                        checkArgument(!optionalConnectedSeedNodeAddress.isPresent(), "We have already a connectedSeedNode. That must not happen.");
                        optionalConnectedSeedNodeAddress = Optional.of(candidate);
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("Send GetAllDataMessage to " + candidate + " failed. " +
                                "That is expected if the seed node is offline. " +
                                "Exception:" + throwable.getMessage());
                        if (!remainingSeedNodeAddresses.isEmpty())
                            log.trace("We try to connect another random seed node from our remaining list. " + remainingSeedNodeAddresses);

                        requestData(remainingSeedNodeAddresses);
                    }
                });
            } else {
                log.info("The seed node ({}) is in the process of authentication.\n" +
                        "We will try again after a pause of 3-5 sec.", candidate);
                listener.onNoSeedNodeAvailable();
                UserThread.runAfterRandomDelay(() -> requestData(remainingSeedNodeAddresses),
                        3, 5, TimeUnit.SECONDS);
            }
        } else {
            log.info("There is no seed node available for requesting data. " +
                    "That is expected if no seed node is online.\n" +
                    "We will try again after a pause of 10-20 sec.");
            listener.onNoSeedNodeAvailable();
            UserThread.runAfterRandomDelay(() -> requestData(optionalSeedNodeAddresses.get()),
                    10, 20, TimeUnit.SECONDS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof DataRequest) {
            // We are a seed node and receive that msg from a new node
            Log.traceCall(message.toString());
            networkNode.sendMessage(connection, new DataResponse(new HashSet<>(dataStorage.getMap().values())));
        } else if (message instanceof DataResponse) {
            // We are the new node which has requested the data
            Log.traceCall(message.toString());
            DataResponse dataResponse = (DataResponse) message;
            HashSet<ProtectedData> set = dataResponse.set;
            // we keep that connection open as the bootstrapping peer will use that for the authentication
            // as we are not authenticated yet the data adding will not be broadcasted 
            connection.getPeerAddressOptional().ifPresent(peerAddress -> set.stream().forEach(e -> dataStorage.add(e, peerAddress)));
            optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> listener.onDataReceived(connectedSeedNodeAddress));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AuthenticationListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPeerAuthenticated(Address peerAddress, Connection connection) {
        if (isSeedNode && dataStorage.getMap().isEmpty()) {
            // We are the seed node and entering the network we request the data from the peer
            UserThread.runAfterRandomDelay(()
                    -> requestDataFromAuthenticatedSeedNode(peerAddress, connection), 2, 5, TimeUnit.SECONDS);
        }

        optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> {
            // We only request the data again if we have initiated the authentication (ConnectionPriority.ACTIVE)
            // We delay a bit to be sure that the authentication state is applied to all threads
            if (connectedSeedNodeAddress.equals(peerAddress) && connection.getConnectionPriority() == ConnectionPriority.ACTIVE) {
                // We are the node (can be a seed node as well) which requested the authentication
                UserThread.runAfter(()
                        -> requestDataFromAuthenticatedSeedNode(peerAddress, connection), 100, TimeUnit.MILLISECONDS);
            }
        });
    }

    // 5. Step after authentication to first seed node we request again the data
    private void requestDataFromAuthenticatedSeedNode(Address peerAddress, Connection connection) {
        Log.traceCall(peerAddress.toString());
        // We have to request the data again as we might have missed pushed data in the meantime
        SettableFuture<Connection> future = networkNode.sendMessage(connection, new DataRequest());
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("requestDataFromAuthenticatedSeedNode from " + peerAddress + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("requestDataFromAuthenticatedSeedNode from " + peerAddress + " failed. " +
                        "Exception:" + throwable.getMessage()
                        + "\nWe will try again to request data from any of our seed nodes.");

                // We will try again to request data from any of our seed nodes. 
                if (optionalSeedNodeAddresses.isPresent())
                    requestData(optionalSeedNodeAddresses.get());
            }
        });
    }
}
