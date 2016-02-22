package io.bitsquare.p2p.storage;

import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

public interface HashMapChangedListener {
    void onAdded(ProtectedStorageEntry data);

    void onRemoved(ProtectedStorageEntry data);
}
