package io.bisq.p2p.storage;

import io.bisq.payload.p2p.storage.ProtectedStorageEntry;

public interface HashMapChangedListener {
    void onAdded(ProtectedStorageEntry data);

    void onRemoved(ProtectedStorageEntry data);
}
