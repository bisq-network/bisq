package io.bitsquare.p2p.storage;

import io.bitsquare.p2p.storage.data.ProtectedData;

public interface HashMapChangedListener {
    void onAdded(ProtectedData entry);

    void onRemoved(ProtectedData entry);
}
