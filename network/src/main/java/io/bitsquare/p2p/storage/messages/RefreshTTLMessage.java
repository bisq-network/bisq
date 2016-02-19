package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;

import java.util.Arrays;

public final class RefreshTTLMessage extends DataBroadcastMessage {
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
}
