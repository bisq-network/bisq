package io.bitsquare.p2p;


public abstract class NetWorkReadyListener implements P2PServiceListener {
    @Override
    public void onTorNodeReady() {
    }

    @Override
    public void onHiddenServicePublished() {
    }

    @Override
    public void onNoSeedNodeAvailable() {
    }

    @Override
    public void onNoPeersAvailable() {
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
    }

    @Override
    public void onRequestingDataCompleted() {
    }

    @Override
    abstract public void onBootstrapped();
}
