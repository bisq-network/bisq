package io.bisq.protobuffer.payload.p2p.storage;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
public class ProtectedMailboxStorageEntry extends ProtectedStorageEntry {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    // Payload
    private final byte[] receiversPubKeyBytes;

    // Domain
    public transient PublicKey receiversPubKey;

    public MailboxStoragePayload getMailboxStoragePayload() {
        return (MailboxStoragePayload) storagePayload;
    }

    public ProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload, PublicKey ownerStoragePubKey,
                                        int sequenceNumber, byte[] signature, PublicKey receiversPubKey) {
        super(mailboxStoragePayload, ownerStoragePubKey, sequenceNumber, signature);

        this.receiversPubKey = receiversPubKey;
        this.receiversPubKeyBytes = new X509EncodedKeySpec(this.receiversPubKey.getEncoded()).getEncoded();
    }

    public ProtectedMailboxStorageEntry(long creationTimeStamp, MailboxStoragePayload mailboxStoragePayload,
                                        byte[] ownerStoragePubKey, int sequenceNumber, byte[] signature,
                                        byte[] receiversPubKeyBytes) {
        super(creationTimeStamp, mailboxStoragePayload, ownerStoragePubKey, sequenceNumber, signature);
        this.receiversPubKeyBytes = receiversPubKeyBytes;
        init();
    }

    private void readObject(java.io.ObjectInputStream in) throws IOException, ClassNotFoundException {
        try {
            in.defaultReadObject();
            init();
        } catch (Throwable t) {
            log.warn("Exception at readObject: " + t.getMessage());
        }
    }

    private void init() {
        try {
            receiversPubKey = KeyFactory.getInstance(Sig.KEY_ALGO, "BC").generatePublic(new X509EncodedKeySpec(receiversPubKeyBytes));
            checkCreationTimeStamp();
        } catch (InvalidKeySpecException | NoSuchAlgorithmException | NoSuchProviderException e) {
            log.error("Couldn't create the pubkey", e);
        }
    }

    public PB.ProtectedMailboxStorageEntry toProto() {
        return PB.ProtectedMailboxStorageEntry.newBuilder().setEntry((PB.ProtectedStorageEntry) super.toProto())
                .setReceiversPubKeyBytes(ByteString.copyFrom(receiversPubKeyBytes)).build();
    }

    @Override
    public String toString() {
        return "ProtectedMailboxData{" +
                "receiversPubKey.hashCode()=" + (receiversPubKey != null ? receiversPubKey.hashCode() : "") +
                "} " + super.toString();
    }
}
