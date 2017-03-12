package io.bisq.p2p.storage;

import io.bisq.p2p.storage.storageentry.ProtectedStorageEntry;

public interface HashMapChangedListener {
    void onAdded(ProtectedStorageEntry data);

    void onRemoved(ProtectedStorageEntry data);
}
