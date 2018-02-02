package io.bisq.network.p2p.network;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.network.p2p.peers.keepalive.messages.KeepAliveMessage;

import java.io.OutputStream;

class ProtoOutputStream {
    private final OutputStream deleage;
    private final Statistic statistic;

    ProtoOutputStream(OutputStream deleage, Statistic statistic) {
        this.deleage = deleage;
        this.statistic = statistic;
    }

    void writeEnvelope(NetworkEnvelope envelope) {
        PB.NetworkEnvelope proto = envelope.toProtoNetworkEnvelope();
        proto.writeDelimitedTo(deleage);
        deleage.flush();

        statistic.addSentBytes(proto.getSerializedSize());
        statistic.addSentMessage(envelope);

        if (!(envelope instanceof KeepAliveMessage)) {
            statistic.updateLastActivityTimestamp();
        }
    }
}
