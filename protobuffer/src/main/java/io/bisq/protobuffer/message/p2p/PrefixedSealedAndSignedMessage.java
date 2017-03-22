package io.bisq.protobuffer.message.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.SendersNodeAddressMessage;
import io.bisq.protobuffer.payload.crypto.SealedAndSigned;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import lombok.EqualsAndHashCode;

import java.util.Arrays;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
public final class PrefixedSealedAndSignedMessage implements MailboxMessage, SendersNodeAddressMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final NodeAddress senderNodeAddress;
    public final SealedAndSigned sealedAndSigned;
    public final byte[] addressPrefixHash;
    private final String uid;

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress, SealedAndSigned sealedAndSigned,
                                          byte[] addressPrefixHash, String uid) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at PrefixedSealedAndSignedMessage");
        this.senderNodeAddress = senderNodeAddress;
        this.sealedAndSigned = sealedAndSigned;
        this.addressPrefixHash = addressPrefixHash;
        this.uid = uid;
    }

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress, SealedAndSigned sealedAndSigned, byte[] addressPrefixHash) {
        this(senderNodeAddress, sealedAndSigned, addressPrefixHash, UUID.randomUUID().toString());
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public PB.Envelope toProto() {
        return PB.Envelope.newBuilder().setPrefixedSealedAndSignedMessage(
                PB.PrefixedSealedAndSignedMessage.newBuilder()
                        .setMessageVersion(messageVersion).setNodeAddress(senderNodeAddress.toProto())
                        .setSealedAndSigned(sealedAndSigned.toProto())
                        .setAddressPrefixHash(ByteString.copyFrom(addressPrefixHash))
                        .setUid(uid)).build();
    }

    @Override
    public String toString() {
        return "PrefixedSealedAndSignedMessage{" +
                "uid=" + uid +
                ", messageVersion=" + messageVersion +
                ", sealedAndSigned=" + sealedAndSigned +
                ", receiverAddressMaskHash.hashCode()=" + Arrays.toString(addressPrefixHash).hashCode() +
                '}';
    }
}
