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

import bisq.common.proto.persistable.PersistablePayload;

import java.security.PublicKey;
/**
 * Stub implementation of a ProtectedStoragePayloadStub implementing the ExpirablePayload & RequiresOwnerIsOnlinePayload
 * & PersistablePayload marker interfaces that can be used in tests to provide canned answers to calls. Useful if the
 * tests don't care about the implementation details of the ProtectedStoragePayload.
 *
 * @see <a href="https://martinfowler.com/articles/mocksArentStubs.html#TheDifferenceBetweenMocksAndStubs">Reference</a>
 */
public class PersistableExpirableProtectedStoragePayloadStub extends ExpirableProtectedStoragePayloadStub
                                                      implements PersistablePayload {

        public PersistableExpirableProtectedStoragePayloadStub(PublicKey ownerPubKey) {
            super(ownerPubKey);
        }

        public PersistableExpirableProtectedStoragePayloadStub(PublicKey ownerPubKey, long ttl) {
            super(ownerPubKey, ttl);
        }
}
