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

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.storage.TestState;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;

import java.security.PublicKey;

import java.util.concurrent.TimeUnit;

/**
 * Stub implementation of a ProtectedStoragePayloadStub implementing the ExpirablePayload & RequiresOwnerIsOnlinePayload
 * marker interfaces that can be used in tests to provide canned answers to calls. Useful if the tests don't care about
 * the implementation details of the ProtectedStoragePayload.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class ExpirableProtectedStoragePayloadStub extends ProtectedStoragePayloadStub
                                                  implements ExpirablePayload, RequiresOwnerIsOnlinePayload {
    private long ttl;

    public ExpirableProtectedStoragePayloadStub(PublicKey ownerPubKey) {
        super(ownerPubKey);
        ttl = TimeUnit.DAYS.toMillis(90);
    }

    public ExpirableProtectedStoragePayloadStub(PublicKey ownerPubKey, long ttl) {
        this(ownerPubKey);
        this.ttl = ttl;
    }

    @Override
    public NodeAddress getOwnerNodeAddress() {
        return TestState.getTestNodeAddress();
    }

    @Override
    public long getTTL() {
        return this.ttl;
    }
}
