package io.bisq.network.p2p;


import io.bisq.network.p2p.network.SetupListener;

public interface P2PServiceListener extends SetupListener {

    void onDataReceived();

    void onNoSeedNodeAvailable();

    void onNoPeersAvailable();

    void onUpdatedDataReceived();
}
