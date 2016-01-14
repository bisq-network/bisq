package io.bitsquare.p2p.peers;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.messages.peers.GetPeersRequest;
import io.bitsquare.p2p.peers.messages.peers.GetPeersResponse;
import io.bitsquare.p2p.peers.messages.peers.PeerExchangeMessage;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class PeerExchangeManager implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(PeerExchangeManager.class);

    private final NetworkNode networkNode;
    private final Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier;
    private final Supplier<Map<Address, Peer>> authenticatedPeersSupplier;
    private final Consumer<Address> removePeerConsumer;
    private final BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer;
    private final ScheduledThreadPoolExecutor executor;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PeerExchangeManager(NetworkNode networkNode,
                               Supplier<Set<ReportedPeer>> authenticatedAndReportedPeersSupplier,
                               Supplier<Map<Address, Peer>> authenticatedPeersSupplier,
                               Consumer<Address> removePeerConsumer,
                               BiConsumer<HashSet<ReportedPeer>, Connection> addReportedPeersConsumer) {
        this.networkNode = networkNode;
        this.authenticatedAndReportedPeersSupplier = authenticatedAndReportedPeersSupplier;
        this.authenticatedPeersSupplier = authenticatedPeersSupplier;
        this.removePeerConsumer = removePeerConsumer;
        this.addReportedPeersConsumer = addReportedPeersConsumer;

        networkNode.addMessageListener(this);

        executor = Utilities.getScheduledThreadPoolExecutor("PeerExchangeManager", 1, 10, 5);
        executor.scheduleAtFixedRate(() -> UserThread.execute(() -> trySendGetPeersRequest()), 7, 7, TimeUnit.MINUTES);
    }

    public void shutDown() {
        Log.traceCall();

        networkNode.removeMessageListener(this);
        MoreExecutors.shutdownAndAwaitTermination(executor, 500, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof PeerExchangeMessage) {
            Log.traceCall(message.toString());
            if (message instanceof GetPeersRequest) {
                GetPeersRequest getPeersRequestMessage = (GetPeersRequest) message;
                HashSet<ReportedPeer> reportedPeers = getPeersRequestMessage.reportedPeers;
                log.trace("Received peers: " + reportedPeers);

                SettableFuture<Connection> future = networkNode.sendMessage(connection,
                        new GetPeersResponse(new HashSet<>(authenticatedAndReportedPeersSupplier.get())));
                Futures.addCallback(future, new FutureCallback<Connection>() {
                    @Override
                    public void onSuccess(Connection connection) {
                        log.trace("GetPeersResponse sent successfully");
                    }

                    @Override
                    public void onFailure(@NotNull Throwable throwable) {
                        log.info("GetPeersResponse sending failed " + throwable.getMessage());
                        removePeerConsumer.accept(getPeersRequestMessage.senderAddress);
                    }
                });
                addReportedPeersConsumer.accept(reportedPeers, connection);
            } else if (message instanceof GetPeersResponse) {
                GetPeersResponse getPeersResponse = (GetPeersResponse) message;
                HashSet<ReportedPeer> reportedPeers = getPeersResponse.reportedPeers;
                log.trace("Received peers: " + reportedPeers);
                addReportedPeersConsumer.accept(reportedPeers, connection);
            }
        }
    }


    private void trySendGetPeersRequest() {
        Set<Peer> connectedPeersList = new HashSet<>(authenticatedPeersSupplier.get().values());
        if (!connectedPeersList.isEmpty()) {
            Log.traceCall();
            connectedPeersList.stream()
                    .forEach(e -> {
                        SettableFuture<Connection> future = networkNode.sendMessage(e.connection,
                                new GetPeersRequest(networkNode.getAddress(), new HashSet<>(authenticatedAndReportedPeersSupplier.get())));
                        Futures.addCallback(future, new FutureCallback<Connection>() {
                            @Override
                            public void onSuccess(Connection connection) {
                                log.trace("sendGetPeersRequest sent successfully");
                            }

                            @Override
                            public void onFailure(@NotNull Throwable throwable) {
                                log.info("sendGetPeersRequest sending failed " + throwable.getMessage());
                                removePeerConsumer.accept(e.address);
                            }
                        });
                    });
        }
    }
}
