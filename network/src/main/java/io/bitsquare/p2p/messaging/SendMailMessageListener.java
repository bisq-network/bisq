package io.bitsquare.p2p.messaging;

public interface SendMailMessageListener {
    void onArrived();

    void onFault();
}
