package io.bisq.network.p2p;


public abstract class BootstrapListener implements P2PServiceListener {
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
    abstract public void onBootstrapComplete();

    @Override
    public void onRequestCustomBridges() {
    }
}
