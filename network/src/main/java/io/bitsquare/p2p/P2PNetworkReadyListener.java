package io.bitsquare.p2p;


public abstract class P2PNetworkReadyListener implements P2PServiceListener {
    @Override
    public void onTorNodeReady() {
    }

    @Override
    public void onHiddenServicePublished() {
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
    }

    @Override
    public void onRequestingDataCompleted() {
    }

    @Override
    abstract public void onFirstPeerAuthenticated();
}
