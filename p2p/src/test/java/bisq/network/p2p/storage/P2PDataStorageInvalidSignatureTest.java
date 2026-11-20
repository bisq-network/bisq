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

package bisq.network.p2p.storage;

import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.Optional;

import org.mockito.MockedStatic;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * Copies of a received entry with an invalid signature cost one signature verification, also when they differ in the
 * creation date, which the signature does not cover. See docs/specifications/network/storage-entry-signatures.md.
 */
public class P2PDataStorageInvalidSignatureTest {
    private TestState testState;
    private Connection connection;

    @BeforeEach
    public void setUp() {
        testState = new TestState();
        connection = mock(Connection.class);
        when(connection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));
    }

    @Test
    public void redatedCopiesOfAnAddWithAnInvalidSignatureCostOneVerification() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        byte[] invalidSignature = signatureOfOtherData(owner);
        ProtectedStorageEntry entry = new ProtectedStorageEntry(payload, owner.getPublic(), 1, invalidSignature,
                testState.clockFake);
        testState.clockFake.increment(1000);
        ProtectedStorageEntry redatedCopy = new ProtectedStorageEntry(payload, owner.getPublic(), 1, invalidSignature,
                testState.clockFake);

        try (MockedStatic<Sig> sig = mockStatic(Sig.class, CALLS_REAL_METHODS)) {
            testState.mockedStorage.onMessage(new AddDataMessage(entry), connection);
            testState.mockedStorage.onMessage(new AddDataMessage(redatedCopy), connection);

            sig.verify(() -> Sig.verify(any(PublicKey.class), any(byte[].class), any(byte[].class)), times(1));
        }
        assertTrue(testState.mockedStorage.getMap().isEmpty());
    }

    @Test
    public void theValidEntryIsStoredAfterACopyWithAnInvalidSignature() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry copyWithInvalidSignature = new ProtectedStorageEntry(payload, owner.getPublic(), 1,
                signatureOfOtherData(owner), testState.clockFake);
        ProtectedStorageEntry validEntry = buildSignedEntry(payload, owner, 1);

        testState.mockedStorage.onMessage(new AddDataMessage(copyWithInvalidSignature), connection);
        testState.mockedStorage.onMessage(new AddDataMessage(validEntry), connection);

        assertEquals(validEntry, testState.mockedStorage.getMap().get(P2PDataStorage.get32ByteHashAsByteArray(payload)));
    }

    @Test
    public void repeatedRemovesWithAnInvalidSignatureCostOneVerification() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry removeWithInvalidSignature = new ProtectedStorageEntry(payload, owner.getPublic(), 2,
                signatureOfOtherData(owner), testState.clockFake);

        try (MockedStatic<Sig> sig = mockStatic(Sig.class, CALLS_REAL_METHODS)) {
            testState.mockedStorage.onMessage(new RemoveDataMessage(removeWithInvalidSignature), connection);
            testState.mockedStorage.onMessage(new RemoveDataMessage(removeWithInvalidSignature), connection);

            sig.verify(() -> Sig.verify(any(PublicKey.class), any(byte[].class), any(byte[].class)), times(1));
        }
    }

    @Test
    public void repeatedRefreshesWithAnInvalidSignatureCostOneVerification() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry validEntry = buildSignedEntry(payload, owner, 1);
        testState.mockedStorage.onMessage(new AddDataMessage(validEntry), connection);
        byte[] hashOfPayload = P2PDataStorage.get32ByteHash(payload);
        RefreshOfferMessage refreshWithInvalidSignature = new RefreshOfferMessage(hashOfDataAndSeqNr(payload, 2),
                signatureOfOtherData(owner), hashOfPayload, 2);

        try (MockedStatic<Sig> sig = mockStatic(Sig.class, CALLS_REAL_METHODS)) {
            testState.mockedStorage.onMessage(refreshWithInvalidSignature, connection);
            testState.mockedStorage.onMessage(refreshWithInvalidSignature, connection);

            sig.verify(() -> Sig.verify(any(PublicKey.class), any(byte[].class), any(byte[].class)), times(1));
        }
        assertEquals(validEntry, testState.mockedStorage.getMap().get(new P2PDataStorage.ByteArray(hashOfPayload)));
    }

    private ProtectedStorageEntry buildSignedEntry(ProtectedStoragePayload payload,
                                                   KeyPair owner,
                                                   int sequenceNumber) throws CryptoException {
        byte[] signature = Sig.sign(owner.getPrivate(), hashOfDataAndSeqNr(payload, sequenceNumber));
        return new ProtectedStorageEntry(payload, owner.getPublic(), sequenceNumber, signature, testState.clockFake);
    }

    private static byte[] hashOfDataAndSeqNr(ProtectedStoragePayload payload, int sequenceNumber) {
        return P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(payload, sequenceNumber));
    }

    // A well-formed signature of other data, so its verification runs completely and fails.
    private static byte[] signatureOfOtherData(KeyPair owner) throws CryptoException {
        return Sig.sign(owner.getPrivate(), Hash.getSha256Hash("other data"));
    }
}
