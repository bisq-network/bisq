package io.bitsquare.crypto;

import io.bitsquare.app.Version;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;

public final class SealedAndSignedMessage implements MailboxMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final SealedAndSigned sealedAndSigned;
    public final Address peerAddress;

    public SealedAndSignedMessage(SealedAndSigned sealedAndSigned, Address peerAddress) {
        this.sealedAndSigned = sealedAndSigned;
        this.peerAddress = peerAddress;
    }

    @Override
    public Address getSenderAddress() {
        return null;
    }
}
