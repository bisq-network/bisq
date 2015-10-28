package io.bitsquare.p2p.network;


public interface SetupListener {

    void onTorNodeReady();

    void onHiddenServiceReady();

    void onSetupFailed(Throwable throwable);

}
