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
import bisq.network.p2p.mocks.MockPayload;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.mocks.PersistableNetworkPayloadStub;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.network.NetworkEnvelope;

import java.util.Optional;

import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests of the P2PDataStore MessageListener interface failure cases. The success cases are covered in the
 * PersistableNetworkPayloadTest and ProtectedStorageEntryTest tests,
 */
public class P2PDataStorageOnMessageHandlerTest {
    private TestState testState;

    @Before
    public void setup() {
        this.testState = new TestState();
    }

    static class UnsupportedBroadcastMessage extends BroadcastMessage {

        UnsupportedBroadcastMessage() {
            super(0);
        }
    }

    @Test
    public void invalidBroadcastMessage() {
        NetworkEnvelope envelope = new MockPayload("Mock");

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        this.testState.mockedStorage.onMessage(envelope, mockedConnection);

        verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
        verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class));
    }

    @Test
    public void unsupportedBroadcastMessage() {
        NetworkEnvelope envelope = new UnsupportedBroadcastMessage();

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.of(TestState.getTestNodeAddress()));

        this.testState.mockedStorage.onMessage(envelope, mockedConnection);

        verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
        verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class));
    }

    @Test
    public void invalidConnectionObject() {
        PersistableNetworkPayload persistableNetworkPayload = new PersistableNetworkPayloadStub(true);
        NetworkEnvelope envelope = new AddPersistableNetworkPayloadMessage(persistableNetworkPayload);

        Connection mockedConnection = mock(Connection.class);
        when(mockedConnection.getPeersNodeAddressOptional()).thenReturn(Optional.empty());

        this.testState.mockedStorage.onMessage(envelope, mockedConnection);

        verify(this.testState.appendOnlyDataStoreListener, never()).onAdded(any(PersistableNetworkPayload.class));
        verify(this.testState.mockBroadcaster, never()).broadcast(any(BroadcastMessage.class), any(NodeAddress.class));
    }
}
