package io.bisq.network.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.SealedAndSigned;
import io.bisq.common.persistance.Msg;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import org.bouncycastle.util.encoders.Hex;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode
public final class PrefixedSealedAndSignedMsg implements MailboxMsg, SendersNodeAddressMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final NodeAddress senderNodeAddress;
    public final SealedAndSigned sealedAndSigned;
    public final byte[] addressPrefixHash;
    private final String uid;

    public PrefixedSealedAndSignedMsg(NodeAddress senderNodeAddress, SealedAndSigned sealedAndSigned,
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
    public PB.Envelope toProto() {
        return Msg.getBaseEnvelope().setPrefixedSealedAndSignedMessage(
                PB.PrefixedSealedAndSignedMessage.newBuilder()
                        .setMessageVersion(messageVersion).setNodeAddress(senderNodeAddress.toProto())
                        .setSealedAndSigned(sealedAndSigned.toProto())
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
