package io.bisq.api.model;

import io.bisq.network.p2p.network.Connection;
import io.bisq.network.p2p.network.OutboundConnection;
import io.bisq.network.p2p.network.Statistic;

public class P2PNetworkConnection {

    public final String nodeAddress;
    public final long sentBytes;
    public final long receivedBytes;
    public final Connection.PeerType peerType;
    public final boolean outbound;
    public final long creationDate;
    public final int roundTripTime;

    public P2PNetworkConnection(Connection connection) {
        final Statistic statistic = connection.getStatistic();
        this.nodeAddress = connection.peersNodeAddressProperty().get().getFullAddress();
        this.sentBytes = statistic.getSentBytes();
        this.receivedBytes = statistic.getReceivedBytes();
        this.peerType = connection.getPeerType();
        this.outbound = connection instanceof OutboundConnection;
        this.creationDate = statistic.getCreationDate().getTime();
        this.roundTripTime = statistic.roundTripTimeProperty().get();
    }
}
