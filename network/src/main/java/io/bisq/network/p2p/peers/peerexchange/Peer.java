package io.bisq.network.p2p.peers.peerexchange;

import io.bisq.common.network.NetworkPayload;
import io.bisq.common.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;
import java.util.Date;

@Getter
@ToString
public final class Peer implements NetworkPayload, PersistablePayload {
    private static final int MAX_FAILED_CONNECTION_ATTEMPTS = 5;

    private final NodeAddress nodeAddress;
    private final Date date;

    @Setter
    private int failedConnectionAttempts = 0;

    public Peer(NodeAddress nodeAddress) {
        this(nodeAddress, new Date());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Peer(NodeAddress nodeAddress, Date date) {
        this.nodeAddress = nodeAddress;
        this.date = date;
    }

    @Override
    public PB.Peer toProtoMessage() {
        return PB.Peer.newBuilder()
                .setNodeAddress(nodeAddress.toProtoMessage())
                .setDate(date.getTime())
                .build();
    }

    public static Peer fromProto(PB.Peer peer) {
        return new Peer(NodeAddress.fromProto(peer.getNodeAddress()),
                Date.from(Instant.ofEpochMilli(peer.getDate())));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void increaseFailedConnectionAttempts() {
        this.failedConnectionAttempts++;
    }

    public boolean tooManyFailedConnectionAttempts() {
        return failedConnectionAttempts >= MAX_FAILED_CONNECTION_ATTEMPTS;
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Peer)) return false;

        Peer that = (Peer) o;

        return !(nodeAddress != null ? !nodeAddress.equals(that.nodeAddress) : that.nodeAddress != null);

    }

    // We don't use the lastActivityDate for identity
    @Override
    public int hashCode() {
        return nodeAddress != null ? nodeAddress.hashCode() : 0;
    }

}
