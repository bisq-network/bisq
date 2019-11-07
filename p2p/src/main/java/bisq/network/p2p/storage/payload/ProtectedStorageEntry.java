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
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.network.NetworkProtoResolver;
import bisq.common.proto.persistable.PersistablePayload;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import java.security.PublicKey;

import java.time.Clock;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@EqualsAndHashCode
@Slf4j
public class ProtectedStorageEntry implements NetworkPayload, PersistablePayload {
    private final ProtectedStoragePayload protectedStoragePayload;
    private final byte[] ownerPubKeyBytes;
    transient private final PublicKey ownerPubKey;
    private final int sequenceNumber;
    private byte[] signature;
    private long creationTimeStamp;

    public ProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                    PublicKey ownerPubKey,
                                    int sequenceNumber,
                                    byte[] signature,
                                    Clock clock) {
        this(protectedStoragePayload,
                Sig.getPublicKeyBytes(ownerPubKey),
                ownerPubKey,
                sequenceNumber,
                signature,
                clock.millis(),
                clock);
    }

    protected ProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                 byte[] ownerPubKeyBytes,
                                 PublicKey ownerPubKey,
                                 int sequenceNumber,
                                 byte[] signature,
                                 long creationTimeStamp,
                                 Clock clock) {

        this.protectedStoragePayload = protectedStoragePayload;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        this.ownerPubKey = ownerPubKey;

        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = creationTimeStamp;

        maybeAdjustCreationTimeStamp(clock);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private ProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                    byte[] ownerPubKeyBytes,
                                    int sequenceNumber,
                                    byte[] signature,
                                    long creationTimeStamp,
                                    Clock clock) {
        this(protectedStoragePayload,
                ownerPubKeyBytes,
                Sig.getPublicKeyFromBytes(ownerPubKeyBytes),
                sequenceNumber,
                signature,
                creationTimeStamp,
                clock);
    }

    public Message toProtoMessage() {
        return protobuf.ProtectedStorageEntry.newBuilder()
                .setStoragePayload((protobuf.StoragePayload) protectedStoragePayload.toProtoMessage())
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))
                .setCreationTimeStamp(creationTimeStamp)
                .build();
    }

    public protobuf.ProtectedStorageEntry toProtectedStorageEntry() {
        return (protobuf.ProtectedStorageEntry) toProtoMessage();

    }

    public static ProtectedStorageEntry fromProto(protobuf.ProtectedStorageEntry proto,
                                                  NetworkProtoResolver resolver) {
        return new ProtectedStorageEntry(
                ProtectedStoragePayload.fromProto(proto.getStoragePayload(), resolver),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getSequenceNumber(),
                proto.getSignature().toByteArray(),
                proto.getCreationTimeStamp(),
                resolver.getClock());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void maybeAdjustCreationTimeStamp(Clock clock) {
        // We don't allow creation date in the future, but we cannot be too strict as clocks are not synced
        if (creationTimeStamp > clock.millis())
            creationTimeStamp = clock.millis();
    }

    public void backDate() {
        if (protectedStoragePayload instanceof ExpirablePayload)
            creationTimeStamp -= ((ExpirablePayload) protectedStoragePayload).getTTL() / 2;
    }

    // TODO: only used in tests so find a better way to test and delete public API
    public void updateSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean isExpired(Clock clock) {
        return protectedStoragePayload instanceof ExpirablePayload &&
                (clock.millis() - creationTimeStamp) > ((ExpirablePayload) protectedStoragePayload).getTTL();
    }

    /*
     * Returns true if the Entry is valid for an add operation. For non-mailbox Entrys, the entry owner must
     * match the payload owner.
     */
    public boolean isValidForAddOperation() {
        // TODO: The code currently supports MailboxStoragePayload objects inside ProtectedStorageEntry. Fix this.
        if (protectedStoragePayload instanceof MailboxStoragePayload) {
            MailboxStoragePayload mailboxStoragePayload = (MailboxStoragePayload) this.getProtectedStoragePayload();
            return mailboxStoragePayload.getSenderPubKeyForAddOperation() != null &&
                    mailboxStoragePayload.getSenderPubKeyForAddOperation().equals(this.getOwnerPubKey());

        } else {
            return this.ownerPubKey != null &&
                    this.protectedStoragePayload != null &&
                    this.ownerPubKey.equals(protectedStoragePayload.getOwnerPubKey());
        }
    }

    /*
     * Returns true if the Entry is valid for a remove operation. For non-mailbox Entrys, the entry owner must
     * match the payload owner.
     */
    public boolean isValidForRemoveOperation() {

        // Same requirements as add()
        return this.isValidForAddOperation();
    }
}
