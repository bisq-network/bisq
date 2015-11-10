package io.bitsquare.crypto;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;

import java.util.Arrays;

public final class SealedAndSignedMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.NETWORK_ID;
    public final SealedAndSigned sealedAndSigned;
    public final byte[] blurredAddressHash;

    public SealedAndSignedMessage(SealedAndSigned sealedAndSigned, byte[] blurredAddressHash) {
        this.sealedAndSigned = sealedAndSigned;
        this.blurredAddressHash = blurredAddressHash;
    }

    @Override
    public Address getSenderAddress() {
        return null;
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "SealedAndSignedMessage{" +
                "networkId=" + networkId +
                ", sealedAndSigned=" + sealedAndSigned +
                ", receiverAddressMaskHash.hashCode()=" + Arrays.toString(blurredAddressHash).hashCode() +
                '}';
    }
}
