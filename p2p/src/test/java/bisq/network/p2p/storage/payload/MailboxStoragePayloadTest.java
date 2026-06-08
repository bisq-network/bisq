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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;

import bisq.common.crypto.SealedAndSigned;
import bisq.common.encoding.canonical.CanonicalEncoder;
import bisq.common.util.ExtraDataMapValidator;

import com.google.protobuf.ByteString;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class MailboxStoragePayloadTest {
    private static final long CUSTOM_TTL = MailboxStoragePayload.TTL - 1;

    @Test
    public void serializeForHashMatchesProtobufForCanonicalSchema() throws NoSuchAlgorithmException {
        assertCanonicalMatchesProtobuf(newMailboxStoragePayload(MailboxStoragePayload.TTL));
        assertCanonicalMatchesProtobuf(newMailboxStoragePayload(CUSTOM_TTL));
        assertCanonicalMatchesProtobuf(newMailboxStoragePayloadWithAddressPrefixHash());
    }

    @Test
    public void singleEntryExtraDataMapSerializesLikeHashMap() throws NoSuchAlgorithmException {
        MailboxStoragePayload payload = newMailboxStoragePayload(CUSTOM_TTL);

        protobuf.StoragePayload treeMapProto = payload.toProtoMessage();
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(MailboxStoragePayload.EXTRA_MAP_KEY_TTL, String.valueOf(CUSTOM_TTL));
        protobuf.MailboxStoragePayload hashMapMailboxProto = treeMapProto.getMailboxStoragePayload().toBuilder()
                .clearExtraData()
                .putAllExtraData(hashMap)
                .build();
        protobuf.StoragePayload hashMapProto = protobuf.StoragePayload.newBuilder()
                .setMailboxStoragePayload(hashMapMailboxProto)
                .build();

        assertArrayEquals(hashMapProto.toByteArray(), treeMapProto.toByteArray());
    }

    @Test
    public void fromProtoConvertsExtraDataMapToTreeMapWithoutChangingSingleEntryBytes() throws NoSuchAlgorithmException {
        protobuf.StoragePayload hashMapProto = buildStoragePayloadWithHashMapExtraData();

        MailboxStoragePayload payload = MailboxStoragePayload.fromProto(hashMapProto.getMailboxStoragePayload());

        assertTrue(payload.getExtraDataMap() instanceof TreeMap);
        assertEquals(CUSTOM_TTL, payload.getTTL());
        assertArrayEquals(hashMapProto.toByteArray(), payload.toProtoMessage().toByteArray());
    }

    @Test
    public void fromProtoPreservesNullExtraDataMap() throws NoSuchAlgorithmException {
        protobuf.MailboxStoragePayload proto = newMailboxStoragePayload(MailboxStoragePayload.TTL)
                .toProtoMessage()
                .getMailboxStoragePayload();

        MailboxStoragePayload payload = MailboxStoragePayload.fromProto(proto);

        assertNull(payload.getExtraDataMap());
        assertEquals(MailboxStoragePayload.TTL, payload.getTTL());
        assertTrue(payload.toProtoMessage().getMailboxStoragePayload().getExtraDataMap().isEmpty());
    }

    @Test
    public void fromProtoSanitizesInvalidExtraDataMap() throws NoSuchAlgorithmException {
        Map<String, String> oversizedMap = new HashMap<>();
        for (int i = 0; i <= ExtraDataMapValidator.MAX_SIZE; i++) {
            oversizedMap.put("key" + i, "value");
        }
        protobuf.MailboxStoragePayload proto = newMailboxStoragePayload(CUSTOM_TTL)
                .toProtoMessage()
                .getMailboxStoragePayload()
                .toBuilder()
                .clearExtraData()
                .putAllExtraData(oversizedMap)
                .build();

        MailboxStoragePayload payload = MailboxStoragePayload.fromProto(proto);

        Map<String, String> extraDataMap = payload.getExtraDataMap();
        assertNotNull(extraDataMap);
        assertTrue(extraDataMap instanceof TreeMap);
        assertTrue(extraDataMap.isEmpty());
        assertTrue(payload.toProtoMessage().getMailboxStoragePayload().getExtraDataMap().isEmpty());
    }

    private static void assertCanonicalMatchesProtobuf(MailboxStoragePayload payload) {
        assertArrayEquals(payload.serialize(), payload.encodeCanonical(CanonicalEncoder.DEFAULT));
    }

    private static protobuf.StoragePayload buildStoragePayloadWithHashMapExtraData() throws NoSuchAlgorithmException {
        protobuf.StoragePayload treeMapProto = newMailboxStoragePayload(CUSTOM_TTL).toProtoMessage();
        Map<String, String> hashMap = new HashMap<>();
        hashMap.put(MailboxStoragePayload.EXTRA_MAP_KEY_TTL, String.valueOf(CUSTOM_TTL));
        return protobuf.StoragePayload.newBuilder()
                .setMailboxStoragePayload(treeMapProto.getMailboxStoragePayload().toBuilder()
                        .clearExtraData()
                        .putAllExtraData(hashMap))
                .build();
    }

    private static MailboxStoragePayload newMailboxStoragePayloadWithAddressPrefixHash()
            throws NoSuchAlgorithmException {
        protobuf.MailboxStoragePayload proto = newMailboxStoragePayload(CUSTOM_TTL)
                .toProtoMessage()
                .getMailboxStoragePayload();
        protobuf.PrefixedSealedAndSignedMessage message = proto.getPrefixedSealedAndSignedMessage()
                .toBuilder()
                .setAddressPrefixHash(ByteString.copyFrom(new byte[] { 0x0d, 0x0e }))
                .setUid("fixed-uid")
                .build();
        return MailboxStoragePayload.fromProto(proto.toBuilder()
                .setPrefixedSealedAndSignedMessage(message)
                .build());
    }

    private static MailboxStoragePayload newMailboxStoragePayload(long ttl) throws NoSuchAlgorithmException {
        KeyPair keyPair = TestUtils.generateKeyPair();
        SealedAndSigned sealedAndSigned = new SealedAndSigned(
                new byte[] { 1 },
                new byte[] { 2 },
                new byte[] { 3 },
                keyPair.getPublic());
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage =
                new PrefixedSealedAndSignedMessage(new NodeAddress("host", 1000), sealedAndSigned);
        return new MailboxStoragePayload(
                prefixedSealedAndSignedMessage,
                keyPair.getPublic(),
                keyPair.getPublic(),
                ttl);
    }
}
