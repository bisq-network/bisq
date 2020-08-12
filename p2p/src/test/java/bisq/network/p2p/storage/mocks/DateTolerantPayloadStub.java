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

import bisq.network.p2p.storage.payload.DateTolerantPayload;

import java.time.Clock;

/**
 * Stub implementation of a ProtectedStoragePayload implementing the DateTolerantPayload marker interface
 * that can be used in tests to provide canned answers to calls. Useful if the tests don't care about the implementation
 * details of the ProtectedStoragePayload.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class DateTolerantPayloadStub implements DateTolerantPayload {
    private final boolean dateInTolerance;

    public DateTolerantPayloadStub(boolean dateInTolerance) {
        this.dateInTolerance = dateInTolerance;
    }

    @Override
    public boolean isDateInTolerance(Clock clock) {
        return this.dateInTolerance;
    }

    @Override
    public protobuf.PersistableNetworkPayload toProtoMessage() {
        throw new UnsupportedOperationException("Stub does not support protobuf");
    }

    @Override
    public byte[] getHash() {
        return new byte[] { 1 };
    }

    @Override
    public boolean verifyHashSize() {
        return true;
    }
}
