package io.bitsquare.p2p;


import io.bitsquare.p2p.network.SetupListener;

public interface P2PServiceListener extends SetupListener {

    void onRequestingDataCompleted();

    void onNoSeedNodeAvailable();

    void onNoPeersAvailable();

    void onBootstrapComplete();
}
