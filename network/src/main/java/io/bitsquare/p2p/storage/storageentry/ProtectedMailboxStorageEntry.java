package io.bitsquare.p2p.storage.storageentry;

import com.google.protobuf.ByteString;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.wire.proto.Messages;
import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.payload.MailboxStoragePayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

public class ProtectedMailboxStorageEntry extends ProtectedStorageEntry {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    public transient PublicKey receiversPubKey;
    private final byte[] receiversPubKeyBytes;

    public MailboxStoragePayload getMailboxStoragePayload() {
        return (MailboxStoragePayload) storagePayload;
    }

    public ProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload, PublicKey ownerStoragePubKey,
                                        int sequenceNumber, byte[] signature, PublicKey receiversPubKey) {
        super(mailboxStoragePayload, ownerStoragePubKey, sequenceNumber, signature);

        this.receiversPubKey = receiversPubKey;
        this.receiversPubKeyBytes = new X509EncodedKeySpec(this.receiversPubKey.getEncoded()).getEncoded();
    }

    public ProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload, PublicKey ownerStoragePubKey,
                                        int sequenceNumber, byte[] signature, byte[] receiversPubKeyBytes) {
        super(mailboxStoragePayload, ownerStoragePubKey, sequenceNumber, signature);
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

    public Messages.ProtectedMailboxStorageEntry toProtoBuf() {
        return Messages.ProtectedMailboxStorageEntry.newBuilder().setEntry((Messages.ProtectedStorageEntry) super.toProtoBuf())
                .setReceiversPubKeyBytes(ByteString.copyFrom(receiversPubKeyBytes)).build();
    }

    @Override
    public String toString() {
        return "ProtectedMailboxData{" +
                "receiversPubKey.hashCode()=" + (receiversPubKey != null ? receiversPubKey.hashCode() : "") +
                "} " + super.toString();
    }
}
