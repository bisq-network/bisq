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
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;

import java.security.KeyPair;
import java.security.PublicKey;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.mockito.MockedStatic;

import org.slf4j.LoggerFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * A signature that failed verification is not verified again for a copy of the entry, and an invalid copy never
 * suppresses the valid entry. See docs/specifications/network/storage-entry-signatures.md.
 */
public class ProtectedStorageEntryInvalidSignatureTest {
    private static final Clock CLOCK = Clock.systemDefaultZone();

    @BeforeEach
    public void setUp() {
        // The hash of a MailboxStoragePayload needs the message version.
        Version.setBaseCryptoNetworkId(1);
    }

    @Test
    public void aCopyWithAnotherCreationDateIsRejectedWithoutANewVerification() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        byte[] invalidSignature = signatureOfOtherData(owner);
        ProtectedStorageEntry entry = new ProtectedStorageEntry(payload, owner.getPublic(), 1, invalidSignature,
                clockAt(1_000));
        ProtectedStorageEntry redatedCopy = new ProtectedStorageEntry(payload, owner.getPublic(), 1, invalidSignature,
                clockAt(2_000));
        assertNotEquals(entry.getCreationTimeStamp(), redatedCopy.getCreationTimeStamp());

        try (MockedStatic<Sig> sig = mockStatic(Sig.class, CALLS_REAL_METHODS)) {
            assertFalse(entry.isValidForAddOperation());
            assertFalse(redatedCopy.isValidForAddOperation());
            assertFalse(redatedCopy.isValidForRemoveOperation());

            sig.verify(() -> Sig.verify(any(PublicKey.class), any(byte[].class), any(byte[].class)), times(1));
        }
    }

    @Test
    public void aCopyWithAnotherSignatureDoesNotSuppressTheValidEntry() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry validEntry = buildSignedEntry(payload, owner, 1);
        ProtectedStorageEntry copyWithInvalidSignature = new ProtectedStorageEntry(payload, owner.getPublic(), 1,
                signatureOfOtherData(owner), CLOCK);

        assertFalse(copyWithInvalidSignature.isValidForAddOperation());
        assertFalse(copyWithInvalidSignature.isValidForAddOperation());

        assertTrue(validEntry.isValidForAddOperation());
        assertTrue(validEntry.isValidForRemoveOperation());
    }

    @Test
    public void aCopyWithAnotherOwnerKeyDoesNotSuppressTheValidEntry() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        KeyPair attacker = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry validEntry = buildSignedEntry(payload, owner, 1);
        ProtectedStorageEntry copyWithOtherOwner = new ProtectedStorageEntry(payload, attacker.getPublic(), 1,
                validEntry.getSignature(), CLOCK);

        assertFalse(copyWithOtherOwner.isValidForAddOperation());

        assertTrue(validEntry.isValidForAddOperation());
    }

    // The receiver key of a mailbox entry is not signed. A copy with another receiver key fails a check after the
    // signature, which must not count as a failed signature.
    @Test
    public void aMailboxCopyWithAnotherReceiverKeyDoesNotSuppressTheValidEntry() throws Exception {
        KeyPair sender = TestUtils.generateKeyPair();
        KeyPair receiver = TestUtils.generateKeyPair();
        KeyPair attacker = TestUtils.generateKeyPair();
        MailboxStoragePayload payload = buildMailboxStoragePayload(sender.getPublic(), receiver.getPublic());
        byte[] signature = Sig.sign(sender.getPrivate(), hashOfDataAndSeqNr(payload, 1));
        ProtectedMailboxStorageEntry validEntry = new ProtectedMailboxStorageEntry(payload, sender.getPublic(), 1,
                signature, receiver.getPublic(), CLOCK);
        ProtectedMailboxStorageEntry copyWithOtherReceiver = new ProtectedMailboxStorageEntry(payload,
                sender.getPublic(), 1, signature, attacker.getPublic(), CLOCK);

        assertFalse(copyWithOtherReceiver.isValidForAddOperation());

        assertTrue(validEntry.isValidForAddOperation());
    }

    // Each invalid entry that a peer makes new writes one line, so the line must not grow with the payload
    @Test
    public void theLogLineOfAFailedVerificationDoesNotHoldThePayload() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        byte[] serializedPayload = new byte[50_000];
        when(payload.toProtoMessage().toByteArray()).thenReturn(serializedPayload);
        when(payload.toProtoMessage().getSerializedSize()).thenReturn(serializedPayload.length);
        ProtectedStorageEntry entry = new ProtectedStorageEntry(payload, owner.getPublic(), 1,
                signatureOfOtherData(owner), CLOCK);

        Logger logger = (Logger) LoggerFactory.getLogger(ProtectedStorageEntry.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            assertFalse(entry.isValidForAddOperation());

            List<ILoggingEvent> warnings = appender.list.stream()
                    .filter(event -> event.getLevel() == Level.WARN)
                    .collect(Collectors.toList());
            assertEquals(1, warnings.size());
            String message = warnings.get(0).getFormattedMessage();
            assertTrue(message.length() < 500, "The log line has " + message.length() + " characters");
            assertTrue(message.contains(ProtectedStoragePayloadStub.class.getSimpleName()));
            assertTrue(message.contains(String.valueOf(serializedPayload.length)));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    // The owner of the entry signed it, but the payload belongs to another key. Each such entry costs the peer one
    // signature and writes the lines of the owner checks.
    @Test
    public void theLogLinesOfAFailedOwnerCheckDoNotHoldThePayload() throws Exception {
        KeyPair payloadOwner = TestUtils.generateKeyPair();
        KeyPair entryOwner = TestUtils.generateKeyPair();
        ProtectedStorageEntry entry = buildSignedEntry(payloadWithALargeDescription(payloadOwner.getPublic()),
                entryOwner, 1);

        List<String> warnings = warningsDuring(ProtectedStorageEntry.class, () -> {
            assertFalse(entry.isValidForAddOperation());
            assertFalse(entry.isValidForRemoveOperation());
        });

        assertFalse(warnings.isEmpty());
        warnings.forEach(warning -> assertTrue(warning.length() < 500,
                "The log line has " + warning.length() + " characters"));
    }

    // A removal is checked as an add first, and its own line followed every failure of that check, also an invalid
    // signature, which costs the peer nothing
    @Test
    public void theLogLinesOfARemovalWithAnInvalidSignatureDoNotHoldThePayload() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        ProtectedStorageEntry entry = new ProtectedStorageEntry(payloadWithALargeDescription(owner.getPublic()),
                owner.getPublic(), 1, signatureOfOtherData(owner), CLOCK);

        List<String> warnings = warningsDuring(ProtectedStorageEntry.class,
                () -> assertFalse(entry.isValidForRemoveOperation()));

        assertFalse(warnings.isEmpty());
        warnings.forEach(warning -> assertTrue(warning.length() < 500,
                "The log line has " + warning.length() + " characters"));
    }

    @Test
    public void theLogLinesOfAFailedMailboxOwnerCheckAreShort() throws Exception {
        KeyPair sender = TestUtils.generateKeyPair();
        KeyPair receiver = TestUtils.generateKeyPair();
        KeyPair entryOwner = TestUtils.generateKeyPair();
        MailboxStoragePayload payload = buildMailboxStoragePayload(sender.getPublic(), receiver.getPublic());
        byte[] signature = Sig.sign(entryOwner.getPrivate(), hashOfDataAndSeqNr(payload, 1));
        ProtectedMailboxStorageEntry entry = new ProtectedMailboxStorageEntry(payload, entryOwner.getPublic(), 1,
                signature, receiver.getPublic(), CLOCK);

        List<String> warnings = warningsDuring(ProtectedMailboxStorageEntry.class, () -> {
            assertFalse(entry.isValidForAddOperation());
            assertFalse(entry.isValidForRemoveOperation());
        });

        assertEquals(2, warnings.size());
        warnings.forEach(warning -> assertTrue(warning.length() < 500,
                "The log line has " + warning.length() + " characters"));
    }

    // A new entry of a stored payload must have the key of the stored entry. The lines of this check are short as well.
    @Test
    public void theLogLinesOfAFailedCheckOfTheStoredKeyAreShort() throws Exception {
        KeyPair owner = TestUtils.generateKeyPair();
        KeyPair other = TestUtils.generateKeyPair();
        ProtectedStoragePayload payload = new ProtectedStoragePayloadStub(owner.getPublic());
        ProtectedStorageEntry stored = buildSignedEntry(payload, owner, 1);
        ProtectedStorageEntry fromAnotherOwner = buildSignedEntry(payload, other, 2);

        KeyPair sender = TestUtils.generateKeyPair();
        KeyPair receiver = TestUtils.generateKeyPair();
        MailboxStoragePayload mailboxPayload = buildMailboxStoragePayload(sender.getPublic(), receiver.getPublic());
        byte[] signature = Sig.sign(sender.getPrivate(), hashOfDataAndSeqNr(mailboxPayload, 1));
        ProtectedMailboxStorageEntry storedMailboxEntry = new ProtectedMailboxStorageEntry(mailboxPayload,
                sender.getPublic(), 1, signature, receiver.getPublic(), CLOCK);
        ProtectedMailboxStorageEntry toAnotherReceiver = new ProtectedMailboxStorageEntry(mailboxPayload,
                sender.getPublic(), 1, signature, other.getPublic(), CLOCK);

        List<String> lines = new ArrayList<>(warningsDuring(ProtectedStorageEntry.class,
                () -> assertFalse(fromAnotherOwner.matchesRelevantPubKey(stored))));
        lines.addAll(warningsDuring(ProtectedMailboxStorageEntry.class, () -> {
            assertFalse(toAnotherReceiver.matchesRelevantPubKey(storedMailboxEntry));
            assertFalse(toAnotherReceiver.matchesRelevantPubKey(stored));
        }));

        assertEquals(3, lines.size());
        lines.forEach(line -> assertTrue(line.length() < 500, "The log line has " + line.length() + " characters"));
    }

    // The description of a payload grows with its content, as the one of an offer does
    private static ProtectedStoragePayload payloadWithALargeDescription(PublicKey owner) {
        ProtectedStoragePayloadStub payload = new ProtectedStoragePayloadStub(owner) {
            @Override
            public String toString() {
                return "x".repeat(50_000);
            }
        };
        when(payload.toProtoMessage().getSerializedSize()).thenReturn(50_000);
        return payload;
    }

    // The lines at WARN and ERROR level
    private static List<String> warningsDuring(Class<?> loggingClass, Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggingClass);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream()
                    .filter(event -> event.getLevel().isGreaterOrEqual(Level.WARN))
                    .map(ILoggingEvent::getFormattedMessage)
                    .collect(Collectors.toList());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static ProtectedStorageEntry buildSignedEntry(ProtectedStoragePayload payload,
                                                          KeyPair owner,
                                                          int sequenceNumber) throws CryptoException {
        byte[] signature = Sig.sign(owner.getPrivate(), hashOfDataAndSeqNr(payload, sequenceNumber));
        return new ProtectedStorageEntry(payload, owner.getPublic(), sequenceNumber, signature, CLOCK);
    }

    private static byte[] hashOfDataAndSeqNr(ProtectedStoragePayload payload, int sequenceNumber) {
        return P2PDataStorage.get32ByteHash(new P2PDataStorage.DataAndSeqNrPair(payload, sequenceNumber));
    }

    // A well-formed signature of other data, so its verification runs completely and fails.
    private static byte[] signatureOfOtherData(KeyPair owner) throws CryptoException {
        return Sig.sign(owner.getPrivate(), Hash.getSha256Hash("other data"));
    }

    private static Clock clockAt(long epochMillis) {
        return Clock.fixed(Instant.ofEpochMilli(epochMillis), ZoneOffset.UTC);
    }

    private static MailboxStoragePayload buildMailboxStoragePayload(PublicKey senderPubKeyForAddOperation,
                                                                    PublicKey ownerPubKey) {
        // Only the hash of the sealed message is needed, so it is the default instance.
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessageMock = mock(PrefixedSealedAndSignedMessage.class);
        protobuf.NetworkEnvelope networkEnvelopeMock = mock(protobuf.NetworkEnvelope.class);
        when(networkEnvelopeMock.getPrefixedSealedAndSignedMessage()).thenReturn(
                protobuf.PrefixedSealedAndSignedMessage.getDefaultInstance());
        when(prefixedSealedAndSignedMessageMock.toProtoNetworkEnvelope()).thenReturn(networkEnvelopeMock);

        return new MailboxStoragePayload(prefixedSealedAndSignedMessageMock,
                senderPubKeyForAddOperation,
                ownerPubKey,
                MailboxStoragePayload.TTL);
    }
}
