package io.bisq.network_messages.p2p.messaging;

public interface SendDirectMessageListener {
    void onArrived();

    void onFault();
}
