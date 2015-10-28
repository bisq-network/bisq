package io.bitsquare.p2p.storage;

import io.bitsquare.p2p.storage.data.ProtectedData;

public interface HashSetChangedListener {
    void onAdded(ProtectedData entry);

    void onRemoved(ProtectedData entry);
}
