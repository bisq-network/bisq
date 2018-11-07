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

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import java.security.PublicKey;

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
    private int sequenceNumber;
    private byte[] signature;
    private long creationTimeStamp;

    public ProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                 PublicKey ownerPubKey,
                                 int sequenceNumber,
                                 byte[] signature) {
        this.protectedStoragePayload = protectedStoragePayload;
        ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
        this.ownerPubKey = ownerPubKey;

        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = System.currentTimeMillis();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected ProtectedStorageEntry(long creationTimeStamp,
                                    ProtectedStoragePayload protectedStoragePayload,
                                    byte[] ownerPubKeyBytes,
                                    int sequenceNumber,
                                    byte[] signature) {
        this.protectedStoragePayload = protectedStoragePayload;
        this.ownerPubKeyBytes = ownerPubKeyBytes;
        ownerPubKey = Sig.getPublicKeyFromBytes(ownerPubKeyBytes);

        this.sequenceNumber = sequenceNumber;
        this.signature = signature;
        this.creationTimeStamp = creationTimeStamp;

        maybeAdjustCreationTimeStamp();
    }

    public Message toProtoMessage() {
        return PB.ProtectedStorageEntry.newBuilder()
                .setStoragePayload((PB.StoragePayload) protectedStoragePayload.toProtoMessage())
                .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                .setSequenceNumber(sequenceNumber)
                .setSignature(ByteString.copyFrom(signature))
                .setCreationTimeStamp(creationTimeStamp)
                .build();
    }

    public PB.ProtectedStorageEntry toProtectedStorageEntry() {
        return (PB.ProtectedStorageEntry) toProtoMessage();

    }

    public static ProtectedStorageEntry fromProto(PB.ProtectedStorageEntry proto,
                                                  NetworkProtoResolver resolver) {
        return new ProtectedStorageEntry(proto.getCreationTimeStamp(),
                ProtectedStoragePayload.fromProto(proto.getStoragePayload(), resolver),
                proto.getOwnerPubKeyBytes().toByteArray(),
                proto.getSequenceNumber(),
                proto.getSignature().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void maybeAdjustCreationTimeStamp() {
        // We don't allow creation date in the future, but we cannot be too strict as clocks are not synced
        if (creationTimeStamp > System.currentTimeMillis())
            creationTimeStamp = System.currentTimeMillis();
    }

    public void refreshTTL() {
        creationTimeStamp = System.currentTimeMillis();
    }

    public void backDate() {
        if (protectedStoragePayload instanceof ExpirablePayload)
            creationTimeStamp -= ((ExpirablePayload) protectedStoragePayload).getTTL() / 2;
    }

    public void updateSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void updateSignature(byte[] signature) {
        this.signature = signature;
    }

    public boolean isExpired() {
        return protectedStoragePayload instanceof ExpirablePayload &&
                (System.currentTimeMillis() - creationTimeStamp) > ((ExpirablePayload) protectedStoragePayload).getTTL();
    }
}
