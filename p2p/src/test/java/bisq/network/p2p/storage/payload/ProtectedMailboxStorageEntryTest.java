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

import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.TestUtils;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.time.Clock;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ProtectedMailboxStorageEntryTest {

    private static MailboxStoragePayload buildMailboxStoragePayload(PublicKey payloadSenderPubKeyForAddOperation,
                                                                    PublicKey payloadOwnerPubKey) {
        return new MailboxStoragePayload(
                mock(PrefixedSealedAndSignedMessage.class), payloadSenderPubKeyForAddOperation, payloadOwnerPubKey);
    }

    private static ProtectedMailboxStorageEntry buildProtectedMailboxStorageEntry(MailboxStoragePayload mailboxStoragePayload, PublicKey ownerKey, PublicKey receiverKey) {
        return new ProtectedMailboxStorageEntry(mailboxStoragePayload, ownerKey, 1, new byte[] { 0 }, receiverKey, Clock.systemDefaultZone());
    }

    // TESTCASE: validForAddOperation() should return true if the Entry owner and sender key specified in payload match
    @Test
    public void isValidForAddOperation() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys.getPublic(), receiverKeys.getPublic());

        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should return false if the Entry owner and sender key specified in payload don't match
    @Test
    public void isValidForAddOperation_EntryOwnerPayloadReceiverMismatch() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys.getPublic(), receiverKeys.getPublic());

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if Entry.receiversPubKey and Payload.ownerPubKey don't match
    // XXXBUGXXX: The current code doesn't validate this mismatch, but it would create an added payload that could never
    // be removed since the remove code requires Entry.receiversPubKey == Payload.ownerPubKey
    @Test
    public void isValidForAddOperation_EntryReceiverPayloadReceiverMismatch() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys.getPublic(), senderKeys.getPublic());

        // should be assertFalse
        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForRemoveOperation() should return true if the Entry owner and payload owner match
    @Test
    public void validForRemove() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, receiverKeys.getPublic(), receiverKeys.getPublic());

        Assert.assertTrue(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: validForRemoveOperation() should return false if the Entry owner and payload owner don't match
    @Test
    public void validForRemoveEntryOwnerPayloadOwnerMismatch() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry = buildProtectedMailboxStorageEntry(mailboxStoragePayload, senderKeys.getPublic(), receiverKeys.getPublic());

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }
}
