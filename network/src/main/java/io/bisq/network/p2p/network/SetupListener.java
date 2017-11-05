package io.bisq.network.p2p.network;

public interface SetupListener {
    void onTorNodeReady();

    void onHiddenServicePublished();

    @SuppressWarnings("unused")
    void onSetupFailed(Throwable throwable);

    void onRequestCustomBridges();
}
