package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.ThreadedPersistableEnvelope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

/**
 *  Goes with {@link SplitStoreService}.
 */
public abstract class SplitStore implements ThreadedPersistableEnvelope {
    @Getter
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();
}
