package io.bisq.protobuffer.message.p2p.storage;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.Marshaller;

import java.util.Arrays;

public final class RefreshTTLMessage extends BroadcastMessage implements Marshaller {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Serialized data has 400 bytes instead of 114 bytes of the raw content ;-(
    // When using Protobuffer that should bets much smaller
    public final byte[] hashOfDataAndSeqNr;     // 32 bytes
    public final byte[] signature;              // 46 bytes
    public final byte[] hashOfPayload;          // 32 bytes
    public final int sequenceNumber;            // 4 bytes

    public RefreshTTLMessage(byte[] hashOfDataAndSeqNr,
                             byte[] signature,
                             byte[] hashOfPayload,
                             int sequenceNumber) {
        this.hashOfDataAndSeqNr = hashOfDataAndSeqNr;
        this.signature = signature;
        this.hashOfPayload = hashOfPayload;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "RefreshTTLMessage{" +
                ", hashOfDataAndSeqNr.hashCode()=" + Arrays.hashCode(hashOfDataAndSeqNr) +
                ", hashOfPayload.hashCode()=" + Arrays.hashCode(hashOfPayload) +
                ", sequenceNumber=" + sequenceNumber +
                ", signature.hashCode()=" + Arrays.hashCode(signature) +
                '}';
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder builder = PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
        return builder.setRefreshTtlMessage(builder.getRefreshTtlMessageBuilder()
                .setHashOfDataAndSeqNr(ByteString.copyFrom(hashOfDataAndSeqNr))
                .setHashOfPayload(ByteString.copyFrom(hashOfPayload))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))).build();
    }
}
