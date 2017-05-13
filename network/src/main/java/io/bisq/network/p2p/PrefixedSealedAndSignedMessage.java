package io.bisq.network.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;

import static com.google.common.base.Preconditions.checkNotNull;

@Value
public final class PrefixedSealedAndSignedMessage implements MailboxMessage, SendersNodeAddressMessage {
    private final int messageVersion = Version.getP2PMessageVersion();
    private final NodeAddress senderNodeAddress;
    private final SealedAndSigned sealedAndSigned;
    private final byte[] addressPrefixHash;
    private final String uid;

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress,
                                          SealedAndSigned sealedAndSigned,
                                          byte[] addressPrefixHash,
                                          String uid) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at PrefixedSealedAndSignedMessage");
        this.senderNodeAddress = senderNodeAddress;
        this.sealedAndSigned = sealedAndSigned;
        this.addressPrefixHash = addressPrefixHash;
        this.uid = uid;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder().setPrefixedSealedAndSignedMessage(
                PB.PrefixedSealedAndSignedMessage.newBuilder()
                        .setMessageVersion(messageVersion).setNodeAddress(senderNodeAddress.toProtoMessage())
                        .setSealedAndSigned(sealedAndSigned.toProtoMessage())
                        .setAddressPrefixHash(ByteString.copyFrom(addressPrefixHash))
                        .setUid(uid)).build();
    }

    public static PrefixedSealedAndSignedMessage fromProto(PB.PrefixedSealedAndSignedMessage proto) {
        return new PrefixedSealedAndSignedMessage(NodeAddress.fromProto(proto.getNodeAddress()),
                SealedAndSigned.fromProto(proto.getSealedAndSigned()),
                proto.getAddressPrefixHash().toByteArray(),
                proto.getUid());

    }
}
