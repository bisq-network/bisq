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
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.time.Clock;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class ProtectedStorageEntryTest {

    private static ProtectedStorageEntry buildProtectedStorageEntry(KeyPair payloadOwner, KeyPair entryOwner) {
        return buildProtectedStorageEntry(new ProtectedStoragePayloadStub(payloadOwner.getPublic()), entryOwner);
    }

    private static ProtectedStorageEntry buildProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                                                    KeyPair entryOwner) {
        return new ProtectedStorageEntry(protectedStoragePayload, entryOwner.getPublic(), 1,
                new byte[] { 0 }, Clock.systemDefaultZone());
    }

    // TESTCASE: validForAddOperation() should return true if the Entry owner and payload owner match
    @Test
    public void isValidForAddOperation() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, ownerKeys);

        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should return false if the Entry owner and payload owner don't match
    @Test
    public void isValidForAddOperation_Mismatch() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        KeyPair notOwnerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, notOwnerKeys);

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the entry is a MailboxStoragePayload wrapped in a
    // ProtectedStorageEntry and the Entry is owned by the sender
    // XXXBUGXXX: Currently, a mis-wrapped MailboxStorageEntry will circumvent the senderPubKeyForAddOperation checks
    @Test
    public void isValidForAddOperation_invalidMailboxPayloadSender() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(
                mock(PrefixedSealedAndSignedMessage.class), senderKeys.getPublic(), receiverKeys.getPublic());

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(mailboxStoragePayload, senderKeys);

        // should be assertFalse
        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the entry is a MailboxStoragePayload wrapped in a
    // ProtectedStorageEntry and the Entry is owned by the receiver
    @Test
    public void isValidForAddOperation_invalidMailboxPayloadReceiver() throws NoSuchAlgorithmException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        MailboxStoragePayload mailboxStoragePayload = new MailboxStoragePayload(
                mock(PrefixedSealedAndSignedMessage.class), senderKeys.getPublic(), receiverKeys.getPublic());

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(mailboxStoragePayload, receiverKeys);

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

}
