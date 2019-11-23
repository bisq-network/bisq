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
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.mocks.ProtectedStoragePayloadStub;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.mockito.MockitoAnnotations;

public class P2PDataStorageProcessGetDataResponse {
    private TestState testState;

    private NodeAddress peerNodeAddress;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.testState = new TestState();

        this.peerNodeAddress = new NodeAddress("peer", 8080);
    }

    static private GetDataResponse buildGetDataResponse(PersistableNetworkPayload persistableNetworkPayload) {
        return buildGetDataResponse(Collections.emptyList(), Collections.singletonList(persistableNetworkPayload));
    }

    static private GetDataResponse buildGetDataResponse(ProtectedStorageEntry protectedStorageEntry) {
        return buildGetDataResponse(Collections.singletonList(protectedStorageEntry), Collections.emptyList());
    }

    static private GetDataResponse buildGetDataResponse(
            List<ProtectedStorageEntry> protectedStorageEntries,
            List<PersistableNetworkPayload> persistableNetworkPayloads) {
        return new GetDataResponse(
                new HashSet<>(protectedStorageEntries),
                new HashSet<>(persistableNetworkPayloads),
                1,
                false);
    }

    /**
     * Generates a unique ProtectedStorageEntry that is valid for add. This is used to initialize P2PDataStorage state
     * so the tests can validate the correct behavior. Adds of identical payloads with different sequence numbers
     * is not supported.
     */
    private ProtectedStorageEntry getProtectedStorageEntryForAdd() throws NoSuchAlgorithmException {
        KeyPair ownerKeys = TestUtils.generateKeyPair();

        ProtectedStoragePayload protectedStoragePayload = new ProtectedStoragePayloadStub(ownerKeys.getPublic());

        ProtectedStorageEntry stub = mock(ProtectedStorageEntry.class);
        when(stub.getOwnerPubKey()).thenReturn(ownerKeys.getPublic());
        when(stub.isValidForAddOperation()).thenReturn(true);
        when(stub.matchesRelevantPubKey(any(ProtectedStorageEntry.class))).thenReturn(true);
        when(stub.getSequenceNumber()).thenReturn(1);
        when(stub.getProtectedStoragePayload()).thenReturn(protectedStoragePayload);

        return stub;
    }

    static class LazyPersistableNetworkPayloadStub extends PersistableNetworkPayloadStub
            implements ProcessOncePersistableNetworkPayload {

        LazyPersistableNetworkPayloadStub(byte[] hash) {
            super(hash);
        }

        LazyPersistableNetworkPayloadStub(boolean validHashSize) {
            super(validHashSize);
        }
    }

    // TESTCASE: GetDataResponse w/ missing PNP is added with no broadcast or listener signal
    // XXXBUGXXX: We signal listeners w/ non ProcessOncePersistableNetworkPayloads
    @Test
    public void processGetDataResponse_newPNPUpdatesState() {
        PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(new byte[] { 1 });

        GetDataResponse getDataResponse = buildGetDataResponse(persistableNetworkPayload);

        TestState.SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, persistableNetworkPayload, true, true, false);
    }

    // TESTCASE: GetDataResponse w/ invalid PNP does nothing (LazyProcessed)
    @Test
    public void processGetDataResponse_newInvalidPNPDoesNothing() {
        PersistableNetworkPayload persistableNetworkPayload = new LazyPersistableNetworkPayloadStub(false);

        GetDataResponse getDataResponse = buildGetDataResponse(persistableNetworkPayload);

        TestState.SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, persistableNetworkPayload, false, false, false);
    }

    // TESTCASE: GetDataResponse w/ existing PNP changes no state
    @Test
    public void processGetDataResponse_duplicatePNPDoesNothing() {
        PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(new byte[] { 1 });
        this.testState.mockedStorage.addPersistableNetworkPayload(persistableNetworkPayload,
                this.peerNodeAddress, false);

        GetDataResponse getDataResponse = buildGetDataResponse(persistableNetworkPayload);

        TestState.SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, persistableNetworkPayload, false, false, false);
    }

    // TESTCASE: GetDataResponse w/ missing PNP is added with no broadcast or listener signal (ProcessOncePersistableNetworkPayload)
    @Test
    public void processGetDataResponse_newPNPUpdatesState_LazyProcessed() {
        PersistableNetworkPayload persistableNetworkPayload = new LazyPersistableNetworkPayloadStub(new byte[] { 1 });

        GetDataResponse getDataResponse = buildGetDataResponse(persistableNetworkPayload);

        TestState.SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, persistableNetworkPayload, true, false, false);
    }

    // TESTCASE: GetDataResponse w/ existing PNP changes no state (ProcessOncePersistableNetworkPayload)
    @Test
    public void processGetDataResponse_duplicatePNPDoesNothing_LazyProcessed() {
        PersistableNetworkPayload persistableNetworkPayload = new LazyPersistableNetworkPayloadStub(new byte[] { 1 });
        this.testState.mockedStorage.addPersistableNetworkPayload(persistableNetworkPayload,
                this.peerNodeAddress, false);

        GetDataResponse getDataResponse = buildGetDataResponse(persistableNetworkPayload);

        TestState.SavedTestState beforeState = this.testState.saveTestState(persistableNetworkPayload);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, persistableNetworkPayload, false, false, false);
    }

    // TESTCASE: Second call to processGetDataResponse adds PNP for non-ProcessOncePersistableNetworkPayloads
    @Test
    public void processGetDataResponse_secondProcessNewPNPUpdatesState() {
        PersistableNetworkPayload addFromFirstProcess = new PersistableNetworkPayloadStub(new byte[] { 1 });
        GetDataResponse getDataResponse = buildGetDataResponse(addFromFirstProcess);

        TestState.SavedTestState beforeState = this.testState.saveTestState(addFromFirstProcess);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, addFromFirstProcess, true, true, false);

        PersistableNetworkPayload addFromSecondProcess = new PersistableNetworkPayloadStub(new byte[] { 2 });
        getDataResponse = buildGetDataResponse(addFromSecondProcess);
        beforeState = this.testState.saveTestState(addFromSecondProcess);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, addFromSecondProcess, true, true, false);
    }

    // TESTCASE: Second call to processGetDataResponse does not add any PNP (LazyProcessed)
    @Test
    public void processGetDataResponse_secondProcessNoPNPUpdates_LazyProcessed() {
        PersistableNetworkPayload addFromFirstProcess = new LazyPersistableNetworkPayloadStub(new byte[] { 1 });
        GetDataResponse getDataResponse = buildGetDataResponse(addFromFirstProcess);

        TestState.SavedTestState beforeState = this.testState.saveTestState(addFromFirstProcess);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, addFromFirstProcess, true, false, false);

        PersistableNetworkPayload addFromSecondProcess = new LazyPersistableNetworkPayloadStub(new byte[] { 2 });
        getDataResponse = buildGetDataResponse(addFromSecondProcess);
        beforeState = this.testState.saveTestState(addFromSecondProcess);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyPersistableAdd(
                beforeState, addFromSecondProcess, false, false, false);
    }

    // TESTCASE: GetDataResponse w/ missing PSE is added with no broadcast or listener signal
    // XXXBUGXXX: We signal listeners for all ProtectedStorageEntrys
    @Test
    public void processGetDataResponse_newPSEUpdatesState() throws NoSuchAlgorithmException {
        ProtectedStorageEntry protectedStorageEntry = getProtectedStorageEntryForAdd();
        GetDataResponse getDataResponse = buildGetDataResponse(protectedStorageEntry);

        TestState.SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyProtectedStorageAdd(
                beforeState, protectedStorageEntry, true, true, false, true);
    }

    // TESTCASE: GetDataResponse w/ existing PSE changes no state
    @Test
    public void processGetDataResponse_duplicatePSEDoesNothing() throws NoSuchAlgorithmException {
        ProtectedStorageEntry protectedStorageEntry = getProtectedStorageEntryForAdd();
        this.testState.mockedStorage.addProtectedStorageEntry(protectedStorageEntry, this.peerNodeAddress, null);

        GetDataResponse getDataResponse = buildGetDataResponse(protectedStorageEntry);

        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        TestState.SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.verifyProtectedStorageAdd(
                beforeState, protectedStorageEntry, false, false, false, false);
    }

    // TESTCASE: GetDataResponse w/ missing PSE is added with no broadcast or listener signal
    // XXXBUGXXX: We signal listeners for all ProtectedStorageEntrys
    @Test
    public void processGetDataResponse_secondCallNewPSEUpdatesState() throws NoSuchAlgorithmException {
        ProtectedStorageEntry protectedStorageEntry = getProtectedStorageEntryForAdd();
        GetDataResponse getDataResponse = buildGetDataResponse(protectedStorageEntry);

        TestState.SavedTestState beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyProtectedStorageAdd(
                beforeState, protectedStorageEntry, true, true, false, true);

        protectedStorageEntry = getProtectedStorageEntryForAdd();
        getDataResponse = buildGetDataResponse(protectedStorageEntry);
        beforeState = this.testState.saveTestState(protectedStorageEntry);
        this.testState.mockedStorage.processGetDataResponse(getDataResponse, this.peerNodeAddress);
        this.testState.verifyProtectedStorageAdd(
                beforeState, protectedStorageEntry, true, true, false, true);
    }
}
