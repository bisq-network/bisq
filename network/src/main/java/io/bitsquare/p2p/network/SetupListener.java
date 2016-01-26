package io.bitsquare.p2p.network;

public interface SetupListener {
    void onTorNodeReady();

    void onHiddenServicePublished();

    @SuppressWarnings("unused")
    void onSetupFailed(Throwable throwable);
}
