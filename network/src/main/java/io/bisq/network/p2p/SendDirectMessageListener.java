package io.bisq.network.p2p;

public interface SendDirectMessageListener {
    void onArrived();

    void onFault();
}
