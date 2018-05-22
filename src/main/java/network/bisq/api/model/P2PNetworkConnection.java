package network.bisq.api.model;

import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.OutboundConnection;
import bisq.network.p2p.network.Statistic;

public class P2PNetworkConnection {

    public String nodeAddress;
    public long sentBytes;
    public long receivedBytes;
    public Connection.PeerType peerType;
    public boolean outbound;
    public long creationDate;
    public int roundTripTime;

    public P2PNetworkConnection() {
    }

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
