package io.bitsquare.crypto;

import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.messaging.MailboxMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SealedAndSignedMessage implements MailboxMessage {
    private static final Logger log = LoggerFactory.getLogger(SealedAndSignedMessage.class);
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
