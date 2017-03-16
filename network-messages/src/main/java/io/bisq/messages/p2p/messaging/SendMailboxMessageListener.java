package io.bisq.messages.p2p.messaging;

public interface SendMailboxMessageListener {
    void onArrived();

    void onStoredInMailbox();

    void onFault(String errorMessage);
}
