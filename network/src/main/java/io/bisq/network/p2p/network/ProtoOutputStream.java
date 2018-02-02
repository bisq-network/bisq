package io.bisq.network.p2p.network;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.peers.keepalive.messages.KeepAliveMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.concurrent.NotThreadSafe;
import java.io.IOException;
import java.io.OutputStream;

@NotThreadSafe
class ProtoOutputStream {
    private static final Logger log = LoggerFactory.getLogger(ProtoOutputStream.class);

    private final OutputStream delegate;
    private final Statistic statistic;

    ProtoOutputStream(OutputStream delegate, Statistic statistic) {
        this.delegate = delegate;
        this.statistic = statistic;
    }

    void writeEnvelope(NetworkEnvelope envelope) {
        try {
            writeEnvelopeOrThrow(envelope);
        } catch (IOException e) {
            log.error("Failed to write envelope", e);
            throw new BisqRuntimeException("Failed to write envelope", e);
        }
    }

    private void writeEnvelopeOrThrow(NetworkEnvelope envelope) throws IOException {
        PB.NetworkEnvelope proto = envelope.toProtoNetworkEnvelope();
        proto.writeDelimitedTo(delegate);
        delegate.flush();

        statistic.addSentBytes(proto.getSerializedSize());
        statistic.addSentMessage(envelope);

        if (!(envelope instanceof KeepAliveMessage)) {
            statistic.updateLastActivityTimestamp();
        }
    }
}
