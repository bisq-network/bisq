package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;

import java.util.Arrays;

public final class RefreshOfferMsg extends BroadcastMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Serialized data has 400 bytes instead of 114 bytes of the raw content ;-(
    // When using Protobuffer that should bets much smaller
    public final byte[] hashOfDataAndSeqNr;     // 32 bytes
    public final byte[] signature;              // 46 bytes
    public final byte[] hashOfPayload;          // 32 bytes
    public final int sequenceNumber;            // 4 bytes

    public RefreshOfferMsg(byte[] hashOfDataAndSeqNr,
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
        return "RefreshOfferMsg{" +
                ", hashOfDataAndSeqNr.hashCode()=" + Arrays.hashCode(hashOfDataAndSeqNr) +
                ", hashOfPayload.hashCode()=" + Arrays.hashCode(hashOfPayload) +
                ", sequenceNumber=" + sequenceNumber +
                ", signature.hashCode()=" + Arrays.hashCode(signature) +
                '}';
    }

    @Override
    public PB.WireEnvelope toProtoMsg() {
        PB.WireEnvelope.Builder builder = NetworkEnvelope.getMsgBuilder();
        return builder.setRefreshOfferMsg(builder.getRefreshOfferMsgBuilder()
                .setHashOfDataAndSeqNr(ByteString.copyFrom(hashOfDataAndSeqNr))
                .setHashOfPayload(ByteString.copyFrom(hashOfPayload))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))).build();
    }

    @Override
    public Message toProtoMessage() {
        return toProtoMsg().getRefreshOfferMsg();
    }
}
