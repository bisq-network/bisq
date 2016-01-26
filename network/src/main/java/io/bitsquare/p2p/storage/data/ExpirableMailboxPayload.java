package io.bitsquare.p2p.storage.data;

import io.bitsquare.app.Version;
import io.bitsquare.crypto.DirectMessage;

import java.security.PublicKey;

public final class ExpirableMailboxPayload implements ExpirablePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private static final long TTL = 10 * 24 * 60 * 60 * 1000; // 10 days

    public final DirectMessage directMessage;
    public final PublicKey senderStoragePublicKey;
    public final PublicKey receiverStoragePublicKey;

    public ExpirableMailboxPayload(DirectMessage directMessage, PublicKey senderStoragePublicKey, PublicKey receiverStoragePublicKey) {
        this.directMessage = directMessage;
        this.senderStoragePublicKey = senderStoragePublicKey;
        this.receiverStoragePublicKey = receiverStoragePublicKey;
    }

    @Override
    public long getTTL() {
        return TTL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ExpirableMailboxPayload)) return false;

        ExpirableMailboxPayload that = (ExpirableMailboxPayload) o;

        return !(directMessage != null ? !directMessage.equals(that.directMessage) : that.directMessage != null);

    }

    @Override
    public int hashCode() {
        return directMessage != null ? directMessage.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MailboxEntry{" +
                "hashCode=" + hashCode() +
                ", sealedAndSignedMessage=" + directMessage +
                '}';
    }
}
