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

import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.mocks.MockMailboxPayload;
import bisq.network.p2p.mocks.MockPayload;
import bisq.network.p2p.mocks.MockSignaturePubKeyAwareMailboxPayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;

import bisq.common.proto.network.NetworkEnvelope;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class MailboxItemTest {
    private static final NodeAddress ENVELOPE_SENDER = new NodeAddress("sender.onion", 9999);
    private static final NodeAddress OTHER_SENDER = new NodeAddress("other.onion", 9999);

    @Test
    public void constructorKeepsDecryptedMessageWhenSenderNodeAddressesMatch() throws NoSuchAlgorithmException {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = decryptedMessageWithPubKey(
                new MockMailboxPayload("msg", ENVELOPE_SENDER));

        MailboxItem mailboxItem = new MailboxItem(protectedMailboxStorageEntry(ENVELOPE_SENDER),
                decryptedMessageWithPubKey);

        assertTrue(mailboxItem.isMine());
        assertFalse(mailboxItem.isInvalidDecryptedMessage());
        assertSame(decryptedMessageWithPubKey, mailboxItem.getDecryptedMessageWithPubKey());
    }

    @Test
    public void constructorDropsDecryptedMessageWhenSenderNodeAddressesMismatch() throws NoSuchAlgorithmException {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = decryptedMessageWithPubKey(
                new MockMailboxPayload("msg", OTHER_SENDER));

        MailboxItem mailboxItem = new MailboxItem(protectedMailboxStorageEntry(ENVELOPE_SENDER),
                decryptedMessageWithPubKey);

        assertFalse(mailboxItem.isMine());
        assertTrue(mailboxItem.isInvalidDecryptedMessage());
    }

    @Test
    public void constructorDropsDecryptedMessageWhenPayloadIsNotMailboxMessage() throws NoSuchAlgorithmException {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = decryptedMessageWithPubKey(new MockPayload("msg"));

        MailboxItem mailboxItem = new MailboxItem(protectedMailboxStorageEntry(ENVELOPE_SENDER),
                decryptedMessageWithPubKey);

        assertFalse(mailboxItem.isMine());
        assertTrue(mailboxItem.isInvalidDecryptedMessage());
    }

    @Test
    public void constructorKeepsDecryptedMessageWhenSenderSignaturePubKeysMatch() throws NoSuchAlgorithmException {
        PublicKey senderSignaturePubKey = TestUtils.generateKeyPair().getPublic();
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = decryptedMessageWithPubKey(
                new MockSignaturePubKeyAwareMailboxPayload("msg", ENVELOPE_SENDER, senderSignaturePubKey),
                senderSignaturePubKey);

        MailboxItem mailboxItem = new MailboxItem(protectedMailboxStorageEntry(ENVELOPE_SENDER),
                decryptedMessageWithPubKey);

        assertTrue(mailboxItem.isMine());
        assertFalse(mailboxItem.isInvalidDecryptedMessage());
        assertSame(decryptedMessageWithPubKey, mailboxItem.getDecryptedMessageWithPubKey());
    }

    @Test
    public void constructorDropsDecryptedMessageWhenSenderSignaturePubKeysMismatch() throws NoSuchAlgorithmException {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = decryptedMessageWithPubKey(
                new MockSignaturePubKeyAwareMailboxPayload("msg",
                        ENVELOPE_SENDER,
                        TestUtils.generateKeyPair().getPublic()),
                TestUtils.generateKeyPair().getPublic());

        MailboxItem mailboxItem = new MailboxItem(protectedMailboxStorageEntry(ENVELOPE_SENDER),
                decryptedMessageWithPubKey);

        assertFalse(mailboxItem.isMine());
        assertTrue(mailboxItem.isInvalidDecryptedMessage());
    }

    private static ProtectedMailboxStorageEntry protectedMailboxStorageEntry(NodeAddress envelopeSenderNodeAddress) {
        ProtectedMailboxStorageEntry protectedMailboxStorageEntry = mock(ProtectedMailboxStorageEntry.class);
        MailboxStoragePayload mailboxStoragePayload = mock(MailboxStoragePayload.class);
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = mock(PrefixedSealedAndSignedMessage.class);

        when(protectedMailboxStorageEntry.getMailboxStoragePayload()).thenReturn(mailboxStoragePayload);
        when(mailboxStoragePayload.getPrefixedSealedAndSignedMessage()).thenReturn(prefixedSealedAndSignedMessage);
        when(prefixedSealedAndSignedMessage.getSenderNodeAddress()).thenReturn(envelopeSenderNodeAddress);

        return protectedMailboxStorageEntry;
    }

    private static DecryptedMessageWithPubKey decryptedMessageWithPubKey(NetworkEnvelope networkEnvelope)
            throws NoSuchAlgorithmException {
        return decryptedMessageWithPubKey(networkEnvelope, TestUtils.generateKeyPair().getPublic());
    }

    private static DecryptedMessageWithPubKey decryptedMessageWithPubKey(NetworkEnvelope networkEnvelope,
                                                                         PublicKey signaturePubKey) {
        return new DecryptedMessageWithPubKey(networkEnvelope, signaturePubKey);
    }
}
