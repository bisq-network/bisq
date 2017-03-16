package io.bisq.network_messages.p2p.messaging;

public interface SendMailboxMessageListener {
    void onArrived();

    void onStoredInMailbox();

    void onFault(String errorMessage);
}
