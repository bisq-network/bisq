package io.bisq.network.p2p.storage;

import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;

public interface PersistableNetworkPayloadMapListener {
    void onAdded(PersistableNetworkPayload payload);
}
