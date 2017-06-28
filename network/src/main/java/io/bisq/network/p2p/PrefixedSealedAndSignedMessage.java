package io.bisq.network.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Value
public final class PrefixedSealedAndSignedMessage extends NetworkEnvelope implements MailboxMessage, SendersNodeAddressMessage {
    private final NodeAddress senderNodeAddress;
    private final SealedAndSigned sealedAndSigned;
    private final byte[] addressPrefixHash;
    private final String uid;

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress,
                                          SealedAndSigned sealedAndSigned,
                                          byte[] addressPrefixHash,
                                          String uid) {
        this(senderNodeAddress, sealedAndSigned, addressPrefixHash, uid, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress,
                                           SealedAndSigned sealedAndSigned,
                                           byte[] addressPrefixHash,
                                           String uid,
                                           int messageVersion) {
        super(messageVersion);
        this.senderNodeAddress = checkNotNull(senderNodeAddress, "senderNodeAddress must not be null");
        this.sealedAndSigned = sealedAndSigned;
        this.addressPrefixHash = addressPrefixHash;
        this.uid = uid;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPrefixedSealedAndSignedMessage(PB.PrefixedSealedAndSignedMessage.newBuilder()
                        .setNodeAddress(senderNodeAddress.toProtoMessage())
                        .setSealedAndSigned(sealedAndSigned.toProtoMessage())
                        .setAddressPrefixHash(ByteString.copyFrom(addressPrefixHash))
                        .setUid(uid))
                .build();
    }

    public static PrefixedSealedAndSignedMessage fromProto(PB.PrefixedSealedAndSignedMessage proto, int messageVersion) {
        return new PrefixedSealedAndSignedMessage(NodeAddress.fromProto(proto.getNodeAddress()),
                SealedAndSigned.fromProto(proto.getSealedAndSigned()),
                proto.getAddressPrefixHash().toByteArray(),
                proto.getUid(),
                messageVersion);
    }

    public static PrefixedSealedAndSignedMessage fromPayloadProto(PB.PrefixedSealedAndSignedMessage proto) {
        // We have the case that an envelope got wrapped into a payload. 
        // We don't check the message version here as it was checked in the carrier envelope already (in connection class)
        // Payloads dont have a message version and are also used for persistence
        // We set the value to -1 to indicate it is set but irrelevant
        return new PrefixedSealedAndSignedMessage(NodeAddress.fromProto(proto.getNodeAddress()),
                SealedAndSigned.fromProto(proto.getSealedAndSigned()),
                proto.getAddressPrefixHash().toByteArray(),
                proto.getUid(),
                -1);
    }
}
