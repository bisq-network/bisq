package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;

import bisq.common.proto.persistable.ThreadedPersistableEnvelope;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

/**
 *  Base class for store implementations using a map with a PersistableNetworkPayload
 *  as the type of the map value.
 */
public abstract class PersistableNetworkPayloadStore implements ThreadedPersistableEnvelope {
    @Getter
    public Map<P2PDataStorage.ByteArray, PersistableNetworkPayload> map = new ConcurrentHashMap<>();
}
