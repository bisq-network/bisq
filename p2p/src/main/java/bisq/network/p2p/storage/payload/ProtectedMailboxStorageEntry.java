/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage.payload;

import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import java.security.PublicKey;

import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

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
