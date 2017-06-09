package io.bisq.network.p2p.peers.peerexchange;

import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.Date;

@Getter
@EqualsAndHashCode(exclude = {"date"})
@ToString
public final class Peer implements NetworkPayload, PersistablePayload {
    private static final int MAX_FAILED_CONNECTION_ATTEMPTS = 5;

    private final NodeAddress nodeAddress;
    private final long date;
    @Setter
    private int failedConnectionAttempts = 0;

    public Peer(NodeAddress nodeAddress) {
        this(nodeAddress, new Date().getTime());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Peer(NodeAddress nodeAddress, long date) {
        this.nodeAddress = nodeAddress;
        this.date = date;
    }

    @Override
    public PB.Peer toProtoMessage() {
        return PB.Peer.newBuilder()
                .setNodeAddress(nodeAddress.toProtoMessage())
                .setDate(date)
                .build();
    }

    public static Peer fromProto(PB.Peer peer) {
        return new Peer(NodeAddress.fromProto(peer.getNodeAddress()),
                peer.getDate());
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

    public Date getDate() {
        return new Date(date);
    }
}
