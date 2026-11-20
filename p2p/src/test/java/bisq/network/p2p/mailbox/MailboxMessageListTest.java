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

package bisq.network.p2p.mailbox;

import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkProtoResolver;

import com.google.protobuf.ByteString;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.time.Clock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MailboxMessageListTest {
    @BeforeEach
    public void setUp() {
        // Hashing the MailboxStoragePayload reaches into the version module, so it has to be initialized.
        Version.setBaseCryptoNetworkId(1);
    }

    // The entries come from the network. A single one we cannot deserialize must not cost us the whole store,
    // because PersistenceManager treats a throw from here as a corrupted file and moves it aside.
    @Test
    public void fromProtoSkipsOnlyTheItemItCannotDeserialize() throws NoSuchAlgorithmException, CryptoException {
        NetworkProtoResolver resolver = mock(NetworkProtoResolver.class);
        when(resolver.getClock()).thenReturn(Clock.systemDefaultZone());

        protobuf.MailboxItem valid = mailboxItem();
        protobuf.MailboxItem withUnusableOwnerPubKey = mailboxItem();
        withUnusableOwnerPubKey = withUnusableOwnerPubKey.toBuilder()
                .setProtectedMailboxStorageEntry(withUnusableOwnerPubKey.getProtectedMailboxStorageEntry().toBuilder()
                        .setReceiversPubKeyBytes(ByteString.copyFrom(new byte[Sig.MAX_SIG_PUB_KEY_BYTES + 1])))
                .build();

        MailboxMessageList list = MailboxMessageList.fromProto(protobuf.MailboxMessageList.newBuilder()
                .addMailboxItem(withUnusableOwnerPubKey)
                .addMailboxItem(valid)
                .build(), resolver);

        assertEquals(1, list.getList().size());
        assertArrayEquals(valid.getProtectedMailboxStorageEntry().getReceiversPubKeyBytes().toByteArray(),
                list.getList().get(0).getProtectedMailboxStorageEntry().getReceiversPubKeyBytes());
    }

    private static protobuf.MailboxItem mailboxItem() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        // The sealed message is never inspected here, it only has to hash, so a default instance is enough.
        PrefixedSealedAndSignedMessage sealedMessage = mock(PrefixedSealedAndSignedMessage.class);
        protobuf.NetworkEnvelope networkEnvelope = mock(protobuf.NetworkEnvelope.class);
        when(networkEnvelope.getPrefixedSealedAndSignedMessage())
                .thenReturn(protobuf.PrefixedSealedAndSignedMessage.getDefaultInstance());
        when(sealedMessage.toProtoNetworkEnvelope()).thenReturn(networkEnvelope);

        MailboxStoragePayload payload = new MailboxStoragePayload(sealedMessage,
                senderKeys.getPublic(),
                receiverKeys.getPublic(),
                MailboxStoragePayload.TTL);
        byte[] signature = Sig.sign(senderKeys.getPrivate(),
                P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(payload, 1)));
        ProtectedMailboxStorageEntry entry = new ProtectedMailboxStorageEntry(payload,
                senderKeys.getPublic(),
                1,
                signature,
                receiverKeys.getPublic(),
                Clock.systemDefaultZone());

        return protobuf.MailboxItem.newBuilder()
                .setProtectedMailboxStorageEntry(entry.toProtoMessage())
                .build();
    }
}
