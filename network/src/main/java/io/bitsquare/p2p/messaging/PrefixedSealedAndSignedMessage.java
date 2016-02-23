package io.bitsquare.p2p.messaging;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;

import java.util.Arrays;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;

public final class PrefixedSealedAndSignedMessage implements MailboxMessage, SendersNodeAddressMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final NodeAddress senderNodeAddress;
    public final SealedAndSigned sealedAndSigned;
    public final byte[] addressPrefixHash;
    private final String uid = UUID.randomUUID().toString();

    public PrefixedSealedAndSignedMessage(NodeAddress senderNodeAddress, SealedAndSigned sealedAndSigned, byte[] addressPrefixHash) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at PrefixedSealedAndSignedMessage");
        this.senderNodeAddress = senderNodeAddress;
        this.sealedAndSigned = sealedAndSigned;
        this.addressPrefixHash = addressPrefixHash;
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
    public String toString() {
        return "SealedAndSignedMessage{" +
                "messageVersion=" + messageVersion +
                ", sealedAndSigned=" + sealedAndSigned +
                ", receiverAddressMaskHash.hashCode()=" + Arrays.toString(addressPrefixHash).hashCode() +
                '}';
    }
}
