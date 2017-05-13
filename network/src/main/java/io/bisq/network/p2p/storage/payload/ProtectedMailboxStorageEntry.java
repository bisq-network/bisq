package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

@Slf4j
@Value
public class ProtectedMailboxStorageEntry extends ProtectedStorageEntry {
    private final byte[] receiversPubKeyBytes;
    private PublicKey receiversPubKey;

    public ProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload,
                                        PublicKey ownerPubKey,
                                        int sequenceNumber,
                                        byte[] signature,
                                        PublicKey receiversPubKey) {
        super(mailboxStoragePayload, ownerPubKey, sequenceNumber, signature);

        this.receiversPubKey = receiversPubKey;
        receiversPubKeyBytes = Sig.getSigPublicKeyBytes(receiversPubKey);
    }

    public ProtectedMailboxStorageEntry(long creationTimeStamp,
                                        MailboxStoragePayload mailboxStoragePayload,
                                        byte[] ownerPubKey,
                                        int sequenceNumber,
                                        byte[] signature,
                                        byte[] receiversPubKeyBytes) {
        super(creationTimeStamp,
                mailboxStoragePayload,
                ownerPubKey,
                sequenceNumber,
                signature);

        this.receiversPubKeyBytes = receiversPubKeyBytes;
        receiversPubKey = Sig.getSigPublicKeyFromBytes(receiversPubKeyBytes);

        checkCreationTimeStamp();
    }

    public PB.ProtectedMailboxStorageEntry toProtoMessage() {
        return PB.ProtectedMailboxStorageEntry.newBuilder()
                .setEntry((PB.ProtectedStorageEntry) super.toProtoMessage())
                .setReceiversPubKeyBytes(ByteString.copyFrom(receiversPubKeyBytes))
                .build();
    }

    public static ProtectedMailboxStorageEntry fromProto(PB.ProtectedMailboxStorageEntry proto,
                                                         NetworkProtoResolver resolver) {
        ProtectedStorageEntry entry = ProtectedStorageEntry.fromProto(proto.getEntry(), resolver);
        return new ProtectedMailboxStorageEntry(
                entry.getCreationTimeStamp(),
                (MailboxStoragePayload) entry.getStoragePayload(),
                entry.getOwnerPubKey().getEncoded(),
                entry.getSequenceNumber(),
                entry.getSignature(),
                proto.getReceiversPubKeyBytes().toByteArray());
    }

    public MailboxStoragePayload getMailboxStoragePayload() {
        return (MailboxStoragePayload) getStoragePayload();
    }
}
