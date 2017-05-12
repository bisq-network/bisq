package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

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
        this.hashOfDataAndSeqNr = hashOfDataAndSeqNr;
        this.signature = signature;
        this.hashOfPayload = hashOfPayload;
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder builder = NetworkEnvelope.getDefaultBuilder();
        return builder.setRefreshOfferMessage(builder.getRefreshOfferMessageBuilder()
                .setHashOfDataAndSeqNr(ByteString.copyFrom(hashOfDataAndSeqNr))
                .setHashOfPayload(ByteString.copyFrom(hashOfPayload))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))).build();
    }

    @Override
    public Message toProtoMessage() {
        return toProtoNetworkEnvelope().getRefreshOfferMessage();
    }
}
