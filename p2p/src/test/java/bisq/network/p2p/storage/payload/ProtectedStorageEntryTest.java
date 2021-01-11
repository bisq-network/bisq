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
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;

import bisq.common.app.Version;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;

import java.time.Clock;
import java.time.Duration;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProtectedStorageEntryTest {
    private static ProtectedStorageEntry buildProtectedStorageEntry(KeyPair payloadOwner, KeyPair entryOwner, int sequenceNumber) throws CryptoException {
        return buildProtectedStorageEntry(new ProtectedStoragePayloadStub(payloadOwner.getPublic()), entryOwner, sequenceNumber);
    }

    private static ProtectedStorageEntry buildProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                                                    KeyPair entryOwner, int sequenceNumber) throws CryptoException {

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(entryOwner.getPrivate(), hashOfDataAndSeqNr);

        return new ProtectedStorageEntry(protectedStoragePayload, entryOwner.getPublic(), sequenceNumber,
                signature, Clock.systemDefaultZone());
    }

    private static MailboxStoragePayload buildMailboxStoragePayload(PublicKey payloadSenderPubKeyForAddOperation,
                                                                    PublicKey payloadOwnerPubKey) {

        // Mock out the PrefixedSealedAndSignedMessage with a version that just serializes to the DEFAULT_INSTANCE
        // in protobuf. This object is never validated in the test, but needs to be hashed as part of the testing path.
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessageMock = mock(PrefixedSealedAndSignedMessage.class);
        protobuf.NetworkEnvelope networkEnvelopeMock = mock(protobuf.NetworkEnvelope.class);
        when(networkEnvelopeMock.getPrefixedSealedAndSignedMessage()).thenReturn(
                protobuf.PrefixedSealedAndSignedMessage.getDefaultInstance());
        when(prefixedSealedAndSignedMessageMock.toProtoNetworkEnvelope()).thenReturn(networkEnvelopeMock);

        return new MailboxStoragePayload(
                prefixedSealedAndSignedMessageMock,
                payloadSenderPubKeyForAddOperation,
                payloadOwnerPubKey,
                MailboxStoragePayload.TTL);
    }

    @Before
    public void SetUp() {
        // Deep in the bowels of protobuf we grab the messageID from the version module. This is required to hash the
        // full MailboxStoragePayload so make sure it is initialized.
        Version.setBaseCryptoNetworkId(1);
    }

    // TESTCASE: validForAddOperation() should return true if the Entry owner and payload owner match
    @Test
    public void isValidForAddOperation() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, ownerKeys, 1);

        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should return false if the Entry owner and payload owner don't match
    @Test
    public void isValidForAddOperation_Mismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        KeyPair notOwnerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, notOwnerKeys, 1);

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the entry is a MailboxStoragePayload wrapped in a
    // ProtectedStorageEntry and the Entry is owned by the sender
    // XXXBUGXXX: Currently, a mis-wrapped MailboxStorageEntry will circumvent the senderPubKeyForAddOperation checks
    @Test
    public void isValidForAddOperation_invalidMailboxPayloadSender() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(
                buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), senderKeys, 1);

        // should be assertFalse
        Assert.assertTrue(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the entry is a MailboxStoragePayload wrapped in a
    // ProtectedStorageEntry and the Entry is owned by the receiver
    @Test
    public void isValidForAddOperation_invalidMailboxPayloadReceiver() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(
                buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), receiverKeys, 1);

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForAddOperation() should fail if the signature isn't valid
    @Test
    public void isValidForAddOperation_BadSignature() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry =
                new ProtectedStorageEntry(protectedStoragePayload, ownerKeys.getPublic(),
                        1, new byte[] { 0 }, Clock.systemDefaultZone());

        Assert.assertFalse(protectedStorageEntry.isValidForAddOperation());
    }

    // TESTCASE: validForRemoveOperation() should return true if the Entry owner and payload owner match
    @Test
    public void isValidForRemoveOperation() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, ownerKeys, 1);

        Assert.assertTrue(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: validForRemoveOperation() should return false if the Entry owner and payload owner don't match
    @Test
    public void isValidForRemoveOperation_Mismatch() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        KeyPair notOwnerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(ownerKeys, notOwnerKeys, 1);

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: validForRemoveOperation() should fail if the entry is a MailboxStoragePayload wrapped in a
    // ProtectedStorageEntry and the Entry is owned by the sender
    // XXXBUGXXX: Currently, a mis-wrapped MailboxStoragePayload will succeed
    @Test
    public void isValidForRemoveOperation_invalidMailboxPayloadSender() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(
                buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), senderKeys, 1);

        // should be assertFalse
        Assert.assertTrue(protectedStorageEntry.isValidForRemoveOperation());
    }

    @Test
    public void isValidForRemoveOperation_invalidMailboxPayloadReceiver() throws NoSuchAlgorithmException, CryptoException {
        KeyPair senderKeys = TestUtils.generateKeyPair();
        KeyPair receiverKeys = TestUtils.generateKeyPair();

        ProtectedStorageEntry protectedStorageEntry = buildProtectedStorageEntry(
                buildMailboxStoragePayload(senderKeys.getPublic(), receiverKeys.getPublic()), receiverKeys, 1);

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: isValidForRemoveOperation() should fail if the signature is bad
    @Test
    public void isValidForRemoveOperation_BadSignature() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry =
                new ProtectedStorageEntry(protectedStoragePayload, ownerKeys.getPublic(),
                        1, new byte[] { 0 }, Clock.systemDefaultZone());

        Assert.assertFalse(protectedStorageEntry.isValidForRemoveOperation());
    }

    // TESTCASE: isMetadataEquals() should succeed if the sequence number changes
    @Test
    public void isMetadataEquals() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        ProtectedStorageEntry seqNrOne = buildProtectedStorageEntry(ownerKeys, ownerKeys, 1);

        ProtectedStorageEntry seqNrTwo = buildProtectedStorageEntry(ownerKeys, ownerKeys, 2);

        Assert.assertTrue(seqNrOne.matchesRelevantPubKey(seqNrTwo));
    }

    // TESTCASE: isMetadataEquals() should fail if the OwnerPubKey changes
    @Test
    public void isMetadataEquals_OwnerPubKeyChanged() throws NoSuchAlgorithmException, CryptoException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();
        KeyPair notOwner = TestUtils.generateKeyPair();

        ProtectedStorageEntry protectedStorageEntryOne = buildProtectedStorageEntry(ownerKeys, ownerKeys, 1);

        ProtectedStorageEntry protectedStorageEntryTwo = buildProtectedStorageEntry(ownerKeys, notOwner, 1);

        Assert.assertFalse(protectedStorageEntryOne.matchesRelevantPubKey(protectedStorageEntryTwo));
    }

    // TESTCASE: Payload implementing ProtectedStoragePayload & PersistableNetworkPayload is invalid
    // We rely on the fact that a payload is either a ProtectedStoragePayload OR PersistableNetworkPayload, but Java
    // does not have a clean way to specify mutually exclusive interfaces.
    //
    // We also want to guarantee that ONLY ProtectedStoragePayload objects are valid as payloads in
    // ProtectedStorageEntrys. This test will give a defense in case future development work breaks that expectation.
    @Test(expected = IllegalArgumentException.class)
    public void ProtectedStoragePayload_PersistableNetworkPayload_incompatible() throws NoSuchAlgorithmException {
        class IncompatiblePayload extends ProtectedStoragePayloadStub implements PersistableNetworkPayload {

            private IncompatiblePayload(PublicKey ownerPubKey) {
                super(ownerPubKey);
            }

            @Override
            public byte[] getHash() {
                return new byte[0];
            }

            @Override
            public boolean verifyHashSize() {
                return true;
            }

            @Override
            public protobuf.PersistableNetworkPayload toProtoMessage() {
                return (protobuf.PersistableNetworkPayload) this.messageMock;
            }
        }

        KeyPair ownerKeys = TestUtils.generateKeyPair();
        IncompatiblePayload incompatiblePayload = new IncompatiblePayload(ownerKeys.getPublic());
        new ProtectedStorageEntry(incompatiblePayload,ownerKeys.getPublic(), 1,
                new byte[] { 0 }, Clock.systemDefaultZone());
    }

    // TESTCASE: PSEs received with future-dated timestamps are updated to be min(currentTime, creationTimeStamp)
    @Test
    public void futureTimestampIsSanitized() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        Clock baseClock = Clock.systemDefaultZone();
        Clock futureClock = Clock.offset(baseClock, Duration.ofDays(1));

        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());
        ProtectedStorageEntry protectedStorageEntry =
                new ProtectedStorageEntry(protectedStoragePayload, Sig.getPublicKeyBytes(ownerKeys.getPublic()),
                        ownerKeys.getPublic(), 1, new byte[] { 0 }, futureClock.millis(), baseClock);

        Assert.assertTrue(protectedStorageEntry.getCreationTimeStamp() <= baseClock.millis());
    }
}
