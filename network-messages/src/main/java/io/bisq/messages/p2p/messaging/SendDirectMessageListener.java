package io.bisq.messages.p2p.messaging;

public interface SendDirectMessageListener {
    void onArrived();

    void onFault();
}
