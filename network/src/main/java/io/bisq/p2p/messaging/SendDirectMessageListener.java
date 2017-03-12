package io.bisq.p2p.messaging;

public interface SendDirectMessageListener {
    void onArrived();

    void onFault();
}
