package io.bisq.network.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import org.bouncycastle.util.encoders.Hex;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
public final class PrefixedSealedAndSignedMessage implements MailboxMessage, SendersNodeAddressMessage {

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
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder().setPrefixedSealedAndSignedMessage(
                PB.PrefixedSealedAndSignedMessage.newBuilder()
                        .setMessageVersion(messageVersion).setNodeAddress(senderNodeAddress.toProtoMessage())
                        .setSealedAndSigned(sealedAndSigned.toProtoMessage())
                        .setAddressPrefixHash(ByteString.copyFrom(addressPrefixHash))
                        .setUid(uid)).build();
    }

    // Hex
    @Override
    public String toString() {
        return "PrefixedSealedAndSignedMessage{" +
                "uid=" + uid +
                ", messageVersion=" + messageVersion +
                ", sealedAndSigned=" + sealedAndSigned +
                ", receiverAddressMaskHash=" + Hex.toHexString(addressPrefixHash) +
                '}';
    }
}
