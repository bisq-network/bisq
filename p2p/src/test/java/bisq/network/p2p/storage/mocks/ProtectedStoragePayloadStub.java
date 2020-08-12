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

package bisq.network.p2p.storage.mocks;

import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.crypto.Sig;

import com.google.protobuf.Message;

import java.security.PublicKey;

import java.util.Map;

import lombok.Getter;

import javax.annotation.Nullable;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stub implementation of a ProtectedStoragePayload that can be used in tests
 * to provide canned answers to calls. Useful if the tests don't care about the implementation
 * details of the ProtectedStoragePayload.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class ProtectedStoragePayloadStub implements ProtectedStoragePayload {
    @Getter
    private PublicKey ownerPubKey;

    protected final Message messageMock;

    public ProtectedStoragePayloadStub(PublicKey ownerPubKey) {
        this.ownerPubKey = ownerPubKey;

        // Need to be able to take the hash which leverages protobuf Messages
        this.messageMock = mock(protobuf.StoragePayload.class);
        when(this.messageMock.toByteArray()).thenReturn(Sig.getPublicKeyBytes(ownerPubKey));
    }

    @Nullable
    @Override
    public Map<String, String> getExtraDataMap() {
        return null;
    }

    @Override
    public Message toProtoMessage() {
        return this.messageMock;
    }
}
