package io.bisq.network.p2p.storage.payload;

import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;

import javax.annotation.Nullable;
import java.security.PublicKey;
import java.util.Map;

/**
 * Messages which support ownership protection (using signatures) and a time to live
 * <p/>
 * Implementations:
 * io.bisq.alert.Alert
 * io.bisq.arbitration.Arbitrator
 * io.bisq.trade.offer.OfferPayload
 */
public interface ProtectedStoragePayload extends ExpirablePayload {
    /**
     * Used for check if the add or remove operation is permitted.
     * Only data owner can add or remove the data.
     * OwnerPubKey has to be equal to the ownerPubKey of the ProtectedStorageEntry
     *
     * @return The public key of the data owner.
     */
    PublicKey getOwnerPubKey();

    // Should be only used in emergency case if we need to add data but do not want to break backward compatibility
    // at the P2P network storage checks. The hash of the object will be used to verify if the data is valid. Any new
    // field in a class would break that hash and therefore break the storage mechanism.
    @Nullable
    Map<String, String> getExtraDataMap();

    static ProtectedStoragePayload fromProto(PB.StoragePayload storagePayload, NetworkProtoResolver networkProtoResolver) {
        return (ProtectedStoragePayload) networkProtoResolver.fromProto(storagePayload);
    }
}
