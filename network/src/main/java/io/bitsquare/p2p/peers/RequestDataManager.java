package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.messages.GetDataRequest;
import io.bitsquare.p2p.storage.messages.GetDataResponse;
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
    private final Listener listener;

    private Optional<Address> optionalConnectedSeedNodeAddress = Optional.empty();
    private Optional<Collection<Address>> optionalSeedNodeAddresses = Optional.empty();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public RequestDataManager(NetworkNode networkNode, P2PDataStorage dataStorage, Listener listener) {
        this.networkNode = networkNode;
        this.dataStorage = dataStorage;
        this.listener = listener;

        networkNode.addMessageListener(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void requestData(Collection<Address> seedNodeAddresses) {
        if (!optionalSeedNodeAddresses.isPresent())
            optionalSeedNodeAddresses = Optional.of(seedNodeAddresses);

        Log.traceCall(seedNodeAddresses.toString());
        if (!seedNodeAddresses.isEmpty()) {
            List<Address> remainingSeedNodeAddresses = new ArrayList<>(seedNodeAddresses);
            Collections.shuffle(remainingSeedNodeAddresses);
            Address candidate = remainingSeedNodeAddresses.remove(0);

            log.info("We try to send a GetAllDataMessage request to a random seed node. " + candidate);

            SettableFuture<Connection> future = networkNode.sendMessage(candidate, new GetDataRequest());
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
            log.info("There is no seed node available for requesting data. " +
                    "That is expected if no seed node is online.\n" +
                    "We will try again after a pause of 20-30 sec.");
            listener.onNoSeedNodeAvailable();

            // We re try after 20-30 sec.
            UserThread.runAfterRandomDelay(() -> requestData(optionalSeedNodeAddresses.get()),
                    20, 30, TimeUnit.SECONDS);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetDataRequest) {
            // We are a seed node and receive that msg from a new node
            Log.traceCall(message.toString());
            networkNode.sendMessage(connection, new GetDataResponse(new HashSet<>(dataStorage.getMap().values())));
        } else if (message instanceof GetDataResponse) {
            // We are the new node which has requested the data
            Log.traceCall(message.toString());
            GetDataResponse getDataResponse = (GetDataResponse) message;
            HashSet<ProtectedData> set = getDataResponse.set;
            // we keep that connection open as the bootstrapping peer will use that for the authentication
            // as we are not authenticated yet the data adding will not be broadcasted 
            set.stream().forEach(e -> dataStorage.add(e, connection.getPeerAddress()));
            optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> listener.onDataReceived(connectedSeedNodeAddress));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AuthenticationListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
        optionalConnectedSeedNodeAddress.ifPresent(connectedSeedNodeAddress -> {
            if (connectedSeedNodeAddress.equals(peerAddress))
                requestDataFromAuthenticatedSeedNode(peerAddress, connection);
        });
    }


    // 5. Step after authentication to first seed node we request again the data
    private void requestDataFromAuthenticatedSeedNode(Address peerAddress, Connection connection) {
        Log.traceCall(peerAddress.toString());
        // We have to request the data again as we might have missed pushed data in the meantime
        SettableFuture<Connection> future = networkNode.sendMessage(connection, new GetDataRequest());
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
