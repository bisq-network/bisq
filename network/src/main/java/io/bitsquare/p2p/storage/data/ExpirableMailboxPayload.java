package io.bitsquare.p2p.storage.data;

import io.bitsquare.app.Version;
import io.bitsquare.crypto.PrefixedSealedAndSignedMessage;

import java.security.PublicKey;

public final class ExpirableMailboxPayload implements ExpirablePayload {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private static final long TTL = 10 * 24 * 60 * 60 * 1000; // 10 days

    public final PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage;
    public final PublicKey senderStoragePublicKey;
    public final PublicKey receiverStoragePublicKey;

    public ExpirableMailboxPayload(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage, PublicKey senderStoragePublicKey, PublicKey receiverStoragePublicKey) {
        this.prefixedSealedAndSignedMessage = prefixedSealedAndSignedMessage;
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

        return !(prefixedSealedAndSignedMessage != null ? !prefixedSealedAndSignedMessage.equals(that.prefixedSealedAndSignedMessage) : that.prefixedSealedAndSignedMessage != null);

    }

    @Override
    public int hashCode() {
        return prefixedSealedAndSignedMessage != null ? prefixedSealedAndSignedMessage.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MailboxEntry{" +
                "hashCode=" + hashCode() +
                ", sealedAndSignedMessage=" + prefixedSealedAndSignedMessage +
                '}';
    }
}
