package io.bisq.p2p;


import io.bisq.p2p.network.SetupListener;

public interface P2PServiceListener extends SetupListener {

    void onRequestingDataCompleted();

    void onNoSeedNodeAvailable();

    void onNoPeersAvailable();

    void onBootstrapComplete();
}
