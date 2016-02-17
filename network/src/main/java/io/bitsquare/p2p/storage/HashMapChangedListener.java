package io.bitsquare.p2p.storage;

public interface HashMapChangedListener {
    void onAdded(ProtectedData data);

    void onRemoved(ProtectedData data);
}
