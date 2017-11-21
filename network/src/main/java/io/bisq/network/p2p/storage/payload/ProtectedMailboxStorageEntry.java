package io.bisq.network.p2p.storage.payload;

import com.google.protobuf.ByteString;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

import java.security.PublicKey;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public class ProtectedMailboxStorageEntry extends ProtectedStorageEntry {
    private final byte[] receiversPubKeyBytes;
    transient private PublicKey receiversPubKey;

    public ProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload,
                                        PublicKey ownerPubKey,
                                        int sequenceNumber,
                                        byte[] signature,
                                        PublicKey receiversPubKey) {
        super(mailboxStoragePayload, ownerPubKey, sequenceNumber, signature);

        this.receiversPubKey = receiversPubKey;
        receiversPubKeyBytes = Sig.getPublicKeyBytes(receiversPubKey);
    }

    public MailboxStoragePayload getMailboxStoragePayload() {
        return (MailboxStoragePayload) getProtectedStoragePayload();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProtectedMailboxStorageEntry(long creationTimeStamp,
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
        receiversPubKey = Sig.getPublicKeyFromBytes(receiversPubKeyBytes);

        maybeAdjustCreationTimeStamp();
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
                (MailboxStoragePayload) entry.getProtectedStoragePayload(),
                entry.getOwnerPubKey().getEncoded(),
                entry.getSequenceNumber(),
                entry.getSignature(),
                proto.getReceiversPubKeyBytes().toByteArray());
    }


    @Override
    public String toString() {
        return "ProtectedMailboxStorageEntry{" +
                "\n     receiversPubKeyBytes=" + Utilities.bytesAsHexString(receiversPubKeyBytes) +
                ",\n     receiversPubKey=" + receiversPubKey +
                "\n} " + super.toString();
    }
}
