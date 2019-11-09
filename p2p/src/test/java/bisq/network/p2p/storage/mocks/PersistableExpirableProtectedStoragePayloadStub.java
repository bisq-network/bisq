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
