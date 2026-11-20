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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.TestUtils;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateSortedTruncatablePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.Sig;
import bisq.common.proto.network.GetDataResponsePriority;

import com.google.protobuf.Message;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

public class P2PDataStorageBuildGetDataResponseTest {
    @ExtendWith(MockitoExtension.class)
    @MockitoSettings(strictness = Strictness.LENIENT) // there are unused stubs in TestState & elsewhere
    abstract static class P2PDataStorageBuildGetDataResponseTestBase {
        // GIVEN null & non-null supportedCapabilities
        private TestState testState;

        abstract GetDataRequest buildGetDataRequest(int nonce, Set<byte[]> knownKeys);

        @Mock
        NetworkNode networkNode;

        private NodeAddress localNodeAddress;

        @BeforeEach
        public void setUp() {
            this.testState = new TestState();

            this.localNodeAddress = new NodeAddress("localhost", 8080);
            when(networkNode.getNodeAddress()).thenReturn(this.localNodeAddress);

            // Set up basic capabilities to ensure message contains it
            Capabilities.app.addAll(Capability.MEDIATION);
        }

        static class RequiredCapabilitiesPNPStub extends PersistableNetworkPayloadStub
                implements CapabilityRequiringPayload {
            Capabilities capabilities;

            RequiredCapabilitiesPNPStub(Capabilities capabilities, byte[] hash) {
                super(hash);
                this.capabilities = capabilities;
            }

            @Override
            public Capabilities getRequiredCapabilities() {
                return capabilities;
            }
        }

        // Like account age witnesses and signed witnesses, which do not count for the size limit
        static class MidPriorityPNPStub extends PersistableNetworkPayloadStub {
            MidPriorityPNPStub(byte[] hash) {
                super(hash);
            }

            @Override
            public GetDataResponsePriority getGetDataResponsePriority() {
                return GetDataResponsePriority.MID;
            }
        }

        // Like trade statistics: added after the other PNP and sorted by date
        static class DateSortedPNPStub extends PersistableNetworkPayloadStub implements DateSortedTruncatablePayload {
            DateSortedPNPStub(byte[] hash) {
                super(hash);
            }

            @Override
            public Date getDate() {
                return new Date(getHash()[0]);
            }

            @Override
            public int maxItems() {
                return 10;
            }
        }

        /**
         * Generates a unique ProtectedStorageEntry that is valid for add. This is used to initialize P2PDataStorage state
         * so the tests can validate the correct behavior. Adds of identical payloads with different sequence numbers
         * is not supported.
         */
        private ProtectedStorageEntry getProtectedStorageEntryForAdd() throws NoSuchAlgorithmException {
            return getProtectedStorageEntryForAdd(null);
        }

        private ProtectedStorageEntry getProtectedStorageEntryForAdd(Capabilities requiredCapabilities)
                throws NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();

            // Payload stub
            ProtectedStoragePayload protectedStoragePayload;

            if (requiredCapabilities == null)
                protectedStoragePayload = mock(ProtectedStoragePayload.class);
            else {
                protectedStoragePayload = mock(ProtectedStoragePayload.class,
                        withSettings().extraInterfaces(CapabilityRequiringPayload.class));
                when(((CapabilityRequiringPayload) protectedStoragePayload).getRequiredCapabilities())
                        .thenReturn(requiredCapabilities);
            }

            Message messageMock = mock(Message.class);
            when(messageMock.toByteArray()).thenReturn(Sig.getPublicKeyBytes(ownerKeys.getPublic()));
            when(protectedStoragePayload.toProtoMessage()).thenReturn(messageMock);
            when(protectedStoragePayload.serialize()).thenReturn(new byte[]{});
            when(protectedStoragePayload.encodeCanonical()).thenReturn(new byte[]{});

            // Entry stub
            ProtectedStorageEntry stub = mock(ProtectedStorageEntry.class);
            when(stub.getOwnerPubKey()).thenReturn(ownerKeys.getPublic());
            when(stub.isValidForAddOperation()).thenReturn(true);
            when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(true);
            when(stub.getSequenceNumber()).thenReturn(1);
            when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);

            return stub;
        }

        // A response gives 25% of its size limit to the PersistableNetworkPayloads and 75% to the ProtectedStorageEntries
        private static int sizeLimitOfPNP() {
            return (int) Math.round(P2PDataStorage.getDefaultMaxSize() * 0.25);
        }

        private static int sizeLimitOfPSE() {
            return (int) Math.round(P2PDataStorage.getDefaultMaxSize() * 0.75);
        }

        private static PersistableNetworkPayload getPersistableNetworkPayloadOfSize(int hash, int serializedSize) {
            PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(new byte[]{(byte) hash});
            when(persistableNetworkPayload.toProtoMessage().getSerializedSize()).thenReturn(serializedSize);
            return persistableNetworkPayload;
        }

        private static PersistableNetworkPayload getMidPriorityPersistableNetworkPayloadOfSize(int hash, int serializedSize) {
            PersistableNetworkPayload persistableNetworkPayload = new MidPriorityPNPStub(new byte[]{(byte) hash});
            when(persistableNetworkPayload.toProtoMessage().getSerializedSize()).thenReturn(serializedSize);
            return persistableNetworkPayload;
        }

        // The size of the largest message that a requester reads, available for the items of a response
        private static int frameSpaceForItems(int numItems) {
            return Connection.getMaxPermittedMessageSize() - P2PDataStorage.GET_DATA_RESPONSE_FRAME_RESERVE -
                    numItems * P2PDataStorage.GET_DATA_RESPONSE_ITEM_FRAME_RESERVE;
        }

        private static PersistableNetworkPayload getDateSortedPersistableNetworkPayloadOfSize(int hash, int serializedSize) {
            PersistableNetworkPayload persistableNetworkPayload = new DateSortedPNPStub(new byte[]{(byte) hash});
            when(persistableNetworkPayload.toProtoMessage().getSerializedSize()).thenReturn(serializedSize);
            return persistableNetworkPayload;
        }

        // An entry with GetDataResponsePriority.LOW, like an offer or a mailbox message, and with a payload hash of its own
        private ProtectedStorageEntry getProtectedStorageEntryOfSize(int serializedSize) throws NoSuchAlgorithmException {
            return getProtectedStorageEntryOfSize(serializedSize, GetDataResponsePriority.LOW);
        }

        private ProtectedStorageEntry getProtectedStorageEntryOfSize(int serializedSize, GetDataResponsePriority priority)
                throws NoSuchAlgorithmException {
            KeyPair ownerKeys = TestUtils.generateKeyPair();
            ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());

            Message entryMessageMock = mock(Message.class);
            when(entryMessageMock.getSerializedSize()).thenReturn(serializedSize);

            ProtectedStorageEntry stub = mock(ProtectedStorageEntry.class);
            when(stub.getOwnerPubKey()).thenReturn(ownerKeys.getPublic());
            when(stub.isValidForAddOperation()).thenReturn(true);
            when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(true);
            when(stub.getSequenceNumber()).thenReturn(1);
            when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);
            when(stub.getGetDataResponsePriority()).thenReturn(priority);
            when(stub.toProtoMessage()).thenReturn(entryMessageMock);

            return stub;
        }

        private static byte[] getHash(ProtectedStorageEntry protectedStorageEntry) {
            return P2PDataStorage.get32ByteHash(protectedStorageEntry.getProtectedStoragePayload());
        }

        // TESTCASE: Given a GetDataRequest w/ unknown PNP, nothing is sent back
        @Test
        public void buildGetDataResponse_unknownPNPDoNothing() {
            PersistableNetworkPayload fromPeer = new PersistableNetworkPayloadStub(new byte[]{1});

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>(Collections.singletonList(fromPeer.getHash())));

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/ known PNP, nothing is sent back
        @Test
        public void buildGetDataResponse_knownPNPDoNothing() {
            PersistableNetworkPayload fromPeerAndLocal = new PersistableNetworkPayloadStub(new byte[]{1});

            this.testState.mockedStorage.addPersistableNetworkPayload(
                    fromPeerAndLocal, this.localNodeAddress, false);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(
                            1,
                            new HashSet<>(Collections.singletonList(fromPeerAndLocal.getHash())));

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP, send it back
        @Test
        public void buildGetDataResponse_unknownPNPSendBack() {
            PersistableNetworkPayload onlyLocal = new PersistableNetworkPayloadStub(new byte[]{1});

            this.testState.mockedStorage.addPersistableNetworkPayload(
                    onlyLocal, this.localNodeAddress, false);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().contains(onlyLocal));
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP, don't send more than truncation limit
        @Test
        public void buildGetDataResponse_unknownPNPSendBackTruncation() {
            PersistableNetworkPayload onlyLocal1 = new PersistableNetworkPayloadStub(new byte[]{1});
            PersistableNetworkPayload onlyLocal2 = new PersistableNetworkPayloadStub(new byte[]{2});

            this.testState.mockedStorage.addPersistableNetworkPayload(
                    onlyLocal1, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(
                    onlyLocal2, this.localNodeAddress, false);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertTrue(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertEquals(1, getDataResponse.getPersistableNetworkPayloadSet().size());
            Set<PersistableNetworkPayload> persistableNetworkPayloadSet = getDataResponse.getPersistableNetworkPayloadSet();

            // We use a set at the filter so it is not deterministic which item get truncated
            assertEquals(1, persistableNetworkPayloadSet.size());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP, but missing required capabilities, nothing is sent back
        @Test
        public void buildGetDataResponse_unknownPNPCapabilitiesMismatchDontSendBack() {
            PersistableNetworkPayload onlyLocal =
                    new RequiredCapabilitiesPNPStub(new Capabilities(Collections.singletonList(Capability.MEDIATION)),
                            new byte[]{1});

            this.testState.mockedStorage.addPersistableNetworkPayload(
                    onlyLocal, this.localNodeAddress, false);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 2, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP that requires capabilities (and they match) send it back
        @Test
        public void buildGetDataResponse_unknownPNPCapabilitiesMatch() {
            PersistableNetworkPayload onlyLocal =
                    new RequiredCapabilitiesPNPStub(new Capabilities(Collections.singletonList(Capability.MEDIATION)),
                            new byte[]{1});

            this.testState.mockedStorage.addPersistableNetworkPayload(
                    onlyLocal, this.localNodeAddress, false);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities(Collections.singletonList(Capability.MEDIATION));
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 2, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().contains(onlyLocal));
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/ unknown PSE, nothing is sent back
        @Test
        public void buildGetDataResponse_unknownPSEDoNothing() throws NoSuchAlgorithmException {
            ProtectedStorageEntry fromPeer = getProtectedStorageEntryForAdd();

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1,
                            new HashSet<>(Collections.singletonList(
                                    P2PDataStorage.get32ByteHash(fromPeer.getProtectedStoragePayload()))));

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/ known PSE, nothing is sent back
        @Test
        public void buildGetDataResponse_knownPSEDoNothing() throws NoSuchAlgorithmException {
            ProtectedStorageEntry fromPeerAndLocal = getProtectedStorageEntryForAdd();

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1,
                            new HashSet<>(Collections.singletonList(
                                    P2PDataStorage.get32ByteHash(fromPeerAndLocal.getProtectedStoragePayload()))));

            this.testState.mockedStorage.addProtectedStorageEntry(
                    fromPeerAndLocal, this.localNodeAddress, null);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PSE, send it back
        //  @Test
        public void buildGetDataResponse_unknownPSESendBack() throws NoSuchAlgorithmException {
            ProtectedStorageEntry onlyLocal = getProtectedStorageEntryForAdd();

            GetDataRequest getDataRequest = this.buildGetDataRequest(1, new HashSet<>());

            this.testState.mockedStorage.addProtectedStorageEntry(
                    onlyLocal, this.localNodeAddress, null);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().contains(onlyLocal));
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP, don't send more than truncation limit
        // @Test
        public void buildGetDataResponse_unknownPSESendBackTruncation() throws NoSuchAlgorithmException {
            ProtectedStorageEntry onlyLocal1 = getProtectedStorageEntryForAdd();
            ProtectedStorageEntry onlyLocal2 = getProtectedStorageEntryForAdd();

            GetDataRequest getDataRequest = this.buildGetDataRequest(1, new HashSet<>());

            this.testState.mockedStorage.addProtectedStorageEntry(
                    onlyLocal1, this.localNodeAddress, null);
            this.testState.mockedStorage.addProtectedStorageEntry(
                    onlyLocal2, this.localNodeAddress, null);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 1, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertTrue(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertEquals(1, getDataResponse.getDataSet().size());
            assertTrue(
                    getDataResponse.getDataSet().contains(onlyLocal1)
                            || getDataResponse.getDataSet().contains(onlyLocal2));
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP, but missing required capabilities, nothing is sent back
        @Test
        public void buildGetDataResponse_unknownPSECapabilitiesMismatchDontSendBack() throws NoSuchAlgorithmException {
            ProtectedStorageEntry onlyLocal =
                    getProtectedStorageEntryForAdd(new Capabilities(Collections.singletonList(Capability.MEDIATION)));

            this.testState.mockedStorage.addProtectedStorageEntry(
                    onlyLocal, this.localNodeAddress, null);

            GetDataRequest getDataRequest = this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities();
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 2, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().isEmpty());
        }

        // TESTCASE: Given a GetDataRequest w/o known PNP that requires capabilities (and they match) send it back
        // @Test
        public void buildGetDataResponse_unknownPSECapabilitiesMatch() throws NoSuchAlgorithmException {
            ProtectedStorageEntry onlyLocal =
                    getProtectedStorageEntryForAdd(new Capabilities(Collections.singletonList(Capability.MEDIATION)));

            this.testState.mockedStorage.addProtectedStorageEntry(
                    onlyLocal, this.localNodeAddress, null);

            GetDataRequest getDataRequest =
                    this.buildGetDataRequest(1, new HashSet<>());

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            Capabilities peerCapabilities = new Capabilities(Collections.singletonList(Capability.MEDIATION));
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    getDataRequest, 2, outPNPTruncated, outPSETruncated, peerCapabilities);

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, getDataResponse.getRequestNonce());
            assertEquals(getDataRequest instanceof GetUpdatedDataRequest, getDataResponse.isGetUpdatedDataResponse());
            assertEquals(getDataResponse.getSupportedCapabilities(), Capabilities.app);
            assertTrue(getDataResponse.getPersistableNetworkPayloadSet().isEmpty());
            assertTrue(getDataResponse.getDataSet().contains(onlyLocal));
        }

        // TESTCASE: PNP which exceed the size limit together are sent one after the other in repeated responses
        @Test
        public void buildGetDataResponse_PNPExceedingSizeLimitTogetherAreSentInRepeatedResponses() {
            int moreThanHalfOfSizeLimit = sizeLimitOfPNP() / 2 + 1;
            PersistableNetworkPayload onlyLocal1 = getPersistableNetworkPayloadOfSize(1, moreThanHalfOfSizeLimit);
            PersistableNetworkPayload onlyLocal2 = getPersistableNetworkPayloadOfSize(2, moreThanHalfOfSizeLimit);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal1, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal2, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse firstResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertTrue(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(1, firstResponse.getPersistableNetworkPayloadSet().size());
            PersistableNetworkPayload sentFirst = firstResponse.getPersistableNetworkPayloadSet().iterator().next();

            // The requester repeats its request, now with the hash of the PNP it has received
            outPNPTruncated = new AtomicBoolean(false);
            Set<byte[]> knownKeys = new HashSet<>(Collections.singletonList(sentFirst.getHash()));
            GetDataResponse secondResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(2, knownKeys), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(Collections.singleton(sentFirst == onlyLocal1 ? onlyLocal2 : onlyLocal1),
                    secondResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: A PNP of exactly the size limit is sent
        @Test
        public void buildGetDataResponse_PNPOfSizeLimitIsSent() {
            PersistableNetworkPayload onlyLocal = getPersistableNetworkPayloadOfSize(1, sizeLimitOfPNP());
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(Collections.singleton(onlyLocal), getDataResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: PSE which exceed the size limit together are sent one after the other in repeated responses
        @Test
        public void buildGetDataResponse_PSEExceedingSizeLimitTogetherAreSentInRepeatedResponses()
                throws NoSuchAlgorithmException {
            int moreThanHalfOfSizeLimit = sizeLimitOfPSE() / 2 + 1;
            ProtectedStorageEntry onlyLocal1 = getProtectedStorageEntryOfSize(moreThanHalfOfSizeLimit);
            ProtectedStorageEntry onlyLocal2 = getProtectedStorageEntryOfSize(moreThanHalfOfSizeLimit);
            this.testState.mockedStorage.addProtectedStorageEntry(onlyLocal1, this.localNodeAddress, null);
            this.testState.mockedStorage.addProtectedStorageEntry(onlyLocal2, this.localNodeAddress, null);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse firstResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertTrue(outPSETruncated.get());
            assertEquals(1, firstResponse.getDataSet().size());
            ProtectedStorageEntry sentFirst = firstResponse.getDataSet().iterator().next();

            // The requester repeats its request, now with the hash of the PSE it has received
            outPSETruncated = new AtomicBoolean(false);
            Set<byte[]> knownKeys = new HashSet<>(Collections.singletonList(getHash(sentFirst)));
            GetDataResponse secondResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(2, knownKeys), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(Collections.singleton(sentFirst == onlyLocal1 ? onlyLocal2 : onlyLocal1),
                    secondResponse.getDataSet());
        }

        // TESTCASE: A PNP above the size limit fits into no response. It is left out, the other PNP are sent, and
        // the response is not marked as truncated, as a repeated request could not deliver that PNP either
        @Test
        public void buildGetDataResponse_PNPAboveSizeLimitIsLeftOut() {
            PersistableNetworkPayload aboveSizeLimit = getPersistableNetworkPayloadOfSize(1, sizeLimitOfPNP() + 1);
            Set<PersistableNetworkPayload> others = new HashSet<>();
            for (int hash = 2; hash <= 6; hash++) {
                others.add(getPersistableNetworkPayloadOfSize(hash, 100));
            }
            // The date sorted PNP are only added if the other PNP have not exceeded the size limit
            others.add(getDateSortedPersistableNetworkPayloadOfSize(7, 100));
            others.add(getDateSortedPersistableNetworkPayloadOfSize(8, 100));
            this.testState.mockedStorage.addPersistableNetworkPayload(aboveSizeLimit, this.localNodeAddress, false);
            others.forEach(other ->
                    this.testState.mockedStorage.addPersistableNetworkPayload(other, this.localNodeAddress, false));

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(others, getDataResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: A date sorted PNP above the size limit is left out as well
        @Test
        public void buildGetDataResponse_dateSortedPNPAboveSizeLimitIsLeftOut() {
            PersistableNetworkPayload aboveSizeLimit =
                    getDateSortedPersistableNetworkPayloadOfSize(1, sizeLimitOfPNP() + 1);
            Set<PersistableNetworkPayload> others = new HashSet<>();
            for (int hash = 2; hash <= 6; hash++) {
                others.add(getDateSortedPersistableNetworkPayloadOfSize(hash, 100));
            }
            this.testState.mockedStorage.addPersistableNetworkPayload(aboveSizeLimit, this.localNodeAddress, false);
            others.forEach(other ->
                    this.testState.mockedStorage.addPersistableNetworkPayload(other, this.localNodeAddress, false));

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(others, getDataResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: A PSE above the size limit, like an offer with oversized extra data, is left out. The other
        // PSE are sent and the response is not marked as truncated
        @Test
        public void buildGetDataResponse_PSEAboveSizeLimitIsLeftOut() throws NoSuchAlgorithmException {
            ProtectedStorageEntry aboveSizeLimit = getProtectedStorageEntryOfSize(sizeLimitOfPSE() + 1);
            Set<ProtectedStorageEntry> others = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                others.add(getProtectedStorageEntryOfSize(2000));
            }
            this.testState.mockedStorage.addProtectedStorageEntry(aboveSizeLimit, this.localNodeAddress, null);
            for (ProtectedStorageEntry other : others) {
                this.testState.mockedStorage.addProtectedStorageEntry(other, this.localNodeAddress, null);
            }

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPNPTruncated.get());
            assertFalse(outPSETruncated.get());
            assertEquals(others, getDataResponse.getDataSet());
        }

        // TESTCASE: A PSE above the size limit does not change that the PSE which fit are sent in repeated responses
        @Test
        public void buildGetDataResponse_PSEAboveSizeLimitDoesNotStopRepeatedResponses() throws NoSuchAlgorithmException {
            int moreThanHalfOfSizeLimit = sizeLimitOfPSE() / 2 + 1;
            ProtectedStorageEntry aboveSizeLimit = getProtectedStorageEntryOfSize(sizeLimitOfPSE() + 1);
            ProtectedStorageEntry onlyLocal1 = getProtectedStorageEntryOfSize(moreThanHalfOfSizeLimit);
            ProtectedStorageEntry onlyLocal2 = getProtectedStorageEntryOfSize(moreThanHalfOfSizeLimit);
            this.testState.mockedStorage.addProtectedStorageEntry(aboveSizeLimit, this.localNodeAddress, null);
            this.testState.mockedStorage.addProtectedStorageEntry(onlyLocal1, this.localNodeAddress, null);
            this.testState.mockedStorage.addProtectedStorageEntry(onlyLocal2, this.localNodeAddress, null);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse firstResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertTrue(outPSETruncated.get());
            assertEquals(1, firstResponse.getDataSet().size());
            ProtectedStorageEntry sentFirst = firstResponse.getDataSet().iterator().next();
            assertTrue(sentFirst == onlyLocal1 || sentFirst == onlyLocal2);

            outPSETruncated = new AtomicBoolean(false);
            Set<byte[]> knownKeys = new HashSet<>(Collections.singletonList(getHash(sentFirst)));
            GetDataResponse secondResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(2, knownKeys), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(outPSETruncated.get());
            assertEquals(Collections.singleton(sentFirst == onlyLocal1 ? onlyLocal2 : onlyLocal1),
                    secondResponse.getDataSet());
        }

        // TESTCASE: A response whose items fill the largest message that a requester reads exactly is not cut
        @Test
        public void buildGetDataResponse_responseAtFrameLimitIsNotCut() {
            int frameSpace = frameSpaceForItems(2);
            PersistableNetworkPayload onlyLocal1 = getMidPriorityPersistableNetworkPayloadOfSize(1, frameSpace / 2);
            PersistableNetworkPayload onlyLocal2 = getMidPriorityPersistableNetworkPayloadOfSize(2,
                    frameSpace - frameSpace / 2);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal1, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal2, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(getDataResponse.isWasTruncated());
            assertEquals(new HashSet<>(Set.of(onlyLocal1, onlyLocal2)), getDataResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: Items of the priority MID do not count for the size limit. One byte above the largest message that a
        // requester reads, the response is cut to fit, and the item left out is sent in a repeated response.
        @Test
        public void buildGetDataResponse_responseAboveFrameLimitIsCutAndRepeated() {
            int frameSpace = frameSpaceForItems(2);
            PersistableNetworkPayload onlyLocal1 = getMidPriorityPersistableNetworkPayloadOfSize(1, frameSpace / 2);
            PersistableNetworkPayload onlyLocal2 = getMidPriorityPersistableNetworkPayloadOfSize(2,
                    frameSpace - frameSpace / 2 + 1);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal1, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal2, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse firstResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            // The larger item is left out first
            assertTrue(firstResponse.isWasTruncated());
            assertTrue(outPNPTruncated.get());
            assertEquals(Collections.singleton(onlyLocal1), firstResponse.getPersistableNetworkPayloadSet());

            outPNPTruncated = new AtomicBoolean(false);
            Set<byte[]> knownKeys = new HashSet<>(Collections.singletonList(onlyLocal1.getHash()));
            GetDataResponse secondResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(2, knownKeys), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(secondResponse.isWasTruncated());
            assertEquals(Collections.singleton(onlyLocal2), secondResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: An item which alone is above the largest message that a requester reads is left out without marking
        // the response as truncated, as no repeated response could hold it
        @Test
        public void buildGetDataResponse_itemAboveFrameLimitIsLeftOutWithoutTruncation() {
            PersistableNetworkPayload aboveFrameLimit = getMidPriorityPersistableNetworkPayloadOfSize(1,
                    frameSpaceForItems(1) + 1);
            PersistableNetworkPayload onlyLocal = getMidPriorityPersistableNetworkPayloadOfSize(2, 100);
            this.testState.mockedStorage.addPersistableNetworkPayload(aboveFrameLimit, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertFalse(getDataResponse.isWasTruncated());
            assertEquals(Collections.singleton(onlyLocal), getDataResponse.getPersistableNetworkPayloadSet());
        }

        // TESTCASE: An entry of the priority HIGH, like a filter, stays in a response which is cut, while other items can
        // be left out, also if it is the largest item
        @Test
        public void buildGetDataResponse_highPriorityEntryStaysInResponseCutToFrameLimit()
                throws NoSuchAlgorithmException {
            int frameLimit = Connection.getMaxPermittedMessageSize();
            ProtectedStorageEntry highPriority = getProtectedStorageEntryOfSize(frameLimit / 2,
                    GetDataResponsePriority.HIGH);
            PersistableNetworkPayload onlyLocal1 = getMidPriorityPersistableNetworkPayloadOfSize(1, frameLimit / 3);
            PersistableNetworkPayload onlyLocal2 = getMidPriorityPersistableNetworkPayloadOfSize(2, frameLimit / 3);
            this.testState.mockedStorage.addProtectedStorageEntry(highPriority, this.localNodeAddress, null);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal1, this.localNodeAddress, false);
            this.testState.mockedStorage.addPersistableNetworkPayload(onlyLocal2, this.localNodeAddress, false);

            AtomicBoolean outPNPTruncated = new AtomicBoolean(false);
            AtomicBoolean outPSETruncated = new AtomicBoolean(false);
            GetDataResponse getDataResponse = this.testState.mockedStorage.buildGetDataResponse(
                    this.buildGetDataRequest(1, new HashSet<>()), 10, outPNPTruncated, outPSETruncated, new Capabilities());

            assertTrue(getDataResponse.isWasTruncated());
            assertEquals(Collections.singleton(highPriority), getDataResponse.getDataSet());
            assertEquals(1, getDataResponse.getPersistableNetworkPayloadSet().size());
        }
    }

    public static class P2PDataStorageBuildGetDataResponseTestPreliminary extends P2PDataStorageBuildGetDataResponseTestBase {

        @Override
        GetDataRequest buildGetDataRequest(int nonce, Set<byte[]> knownKeys) {
            return new PreliminaryGetDataRequest(nonce, knownKeys);
        }
    }

    public static class P2PDataStorageBuildGetDataResponseTestUpdated extends P2PDataStorageBuildGetDataResponseTestBase {

        @Override
        GetDataRequest buildGetDataRequest(int nonce, Set<byte[]> knownKeys) {
            return new GetUpdatedDataRequest(new NodeAddress("peer", 10), nonce, knownKeys);
        }
    }
}
