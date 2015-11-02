package io.bitsquare.crypto;

import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;

public class SealedAndSignedMessage implements MailboxMessage {
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
