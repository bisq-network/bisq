package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;

@Slf4j
@Value
public class ProtectedMailboxStorageEntry extends ProtectedStorageEntry {
    private final byte[] receiversPubKeyBytes;
    private PublicKey receiversPubKey;

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
        receiversPubKey = Sig.getSigPublicKeyFromBytes(receiversPubKeyBytes);
        checkCreationTimeStamp();
    }

    public PB.ProtectedMailboxStorageEntry toProtoMessage() {
        return PB.ProtectedMailboxStorageEntry.newBuilder().setEntry((PB.ProtectedStorageEntry) super.toProtoMessage())
                .setReceiversPubKeyBytes(ByteString.copyFrom(receiversPubKeyBytes)).build();
    }

    public MailboxStoragePayload getMailboxStoragePayload() {
        return (MailboxStoragePayload) getStoragePayload();
    }

}
