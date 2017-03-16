package io.bisq.p2p;

public interface SendDirectMessageListener {
    void onArrived();

    void onFault();
}
