package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class RefreshOfferMessage extends BroadcastMessage {
    private final byte[] hashOfDataAndSeqNr;     // 32 bytes
    private final byte[] signature;              // 46 bytes
    private final byte[] hashOfPayload;          // 32 bytes
    private final int sequenceNumber;            // 4 bytes

    public RefreshOfferMessage(byte[] hashOfDataAndSeqNr,
                               byte[] signature,
                               byte[] hashOfPayload,
                               int sequenceNumber) {
        this(hashOfDataAndSeqNr, signature, hashOfPayload, sequenceNumber, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RefreshOfferMessage(byte[] hashOfDataAndSeqNr,
                                byte[] signature,
                                byte[] hashOfPayload,
                                int sequenceNumber,
                                int messageVersion) {
        super(messageVersion);
        this.hashOfDataAndSeqNr = hashOfDataAndSeqNr;
        this.signature = signature;
        this.hashOfPayload = hashOfPayload;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setRefreshOfferMessage(PB.RefreshOfferMessage.newBuilder()
                        .setHashOfDataAndSeqNr(ByteString.copyFrom(hashOfDataAndSeqNr))
                        .setSignature(ByteString.copyFrom(signature))
                        .setHashOfPayload(ByteString.copyFrom(hashOfPayload))
                        .setSequenceNumber(sequenceNumber))
                .build();
    }

    public static RefreshOfferMessage fromProto(PB.RefreshOfferMessage proto, int messageVersion) {
        return new RefreshOfferMessage(proto.getHashOfDataAndSeqNr().toByteArray(),
                proto.getSignature().toByteArray(),
                proto.getHashOfPayload().toByteArray(),
                proto.getSequenceNumber(),
                messageVersion);
    }
}
