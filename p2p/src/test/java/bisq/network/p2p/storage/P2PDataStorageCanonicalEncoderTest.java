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
 * You should have received a copy of the GNU Affero General Public
 * License along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage;

import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;
import bisq.common.encoding.canonical.Canonical;
import bisq.common.encoding.canonical.CanonicalEncoder;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;

import org.junit.jupiter.api.Test;

import java.security.PublicKey;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class P2PDataStorageCanonicalEncoderTest {
    @Test
    public void dataAndSeqNrPairEncodeCanonicalMatchesProtobuf() {
        P2PDataStorage.DataAndSeqNrPair pair = new P2PDataStorage.DataAndSeqNrPair(
                new StoragePayloadStub(),
                123);

        assertArrayEquals(pair.toProtoMessage().toByteArray(),
                pair.encodeCanonical(CanonicalEncoder.DEFAULT));
        assertArrayEquals(pair.encodeCanonical(CanonicalEncoder.DEFAULT),
                pair.serializeForHash());
    }

    @Test
    public void dataAndSeqNrPairDelegatesPayloadFieldToSerializeForHash() {
        P2PDataStorage.DataAndSeqNrPair pair = new P2PDataStorage.DataAndSeqNrPair(
                new CanonicalStoragePayloadStub(),
                7);

        byte[] canonicalBytes = pair.encodeCanonical(CanonicalEncoder.DEFAULT);

        assertArrayEquals(new byte[]{
                        0x0a, 0x02, 0x08, 0x2a,
                        0x10, 0x07},
                canonicalBytes);
        assertArrayEquals(canonicalBytes, pair.serializeForHash());
        assertFalse(Arrays.equals(pair.toProtoMessage().toByteArray(), canonicalBytes));
    }

    private static final class StoragePayloadStub implements ProtectedStoragePayload {
        private final PublicKey ownerPubKey = Sig.generateKeyPair().getPublic();

        @Override
        public PublicKey getOwnerPubKey() {
            return ownerPubKey;
        }

        @Override
        public Message toProtoMessage() {
            byte[] ownerPubKeyBytes = Sig.getPublicKeyBytes(ownerPubKey);
            protobuf.MailboxStoragePayload mailboxStoragePayload = protobuf.MailboxStoragePayload.newBuilder()
                    .setSenderPubKeyForAddOperationBytes(ByteString.copyFrom(new byte[]{0x01, 0x02}))
                    .setOwnerPubKeyBytes(ByteString.copyFrom(ownerPubKeyBytes))
                    .build();
            return protobuf.StoragePayload.newBuilder()
                    .setMailboxStoragePayload(mailboxStoragePayload)
                    .build();
        }
    }

    private static final class CanonicalStoragePayloadStub implements ProtectedStoragePayload, Canonical {
        private final PublicKey ownerPubKey = Sig.generateKeyPair().getPublic();

        @Override
        public PublicKey getOwnerPubKey() {
            return ownerPubKey;
        }

        @Override
        public Message toProtoMessage() {
            return protobuf.StoragePayload.newBuilder()
                    .setAlert(protobuf.Alert.newBuilder()
                            .setMessage("legacy-protobuf-payload"))
                    .build();
        }

        @Override
        public byte[] encodeCanonical(CanonicalEncoder canonicalEncoder) {
            return new byte[]{0x08, 0x2a};
        }
    }
}
