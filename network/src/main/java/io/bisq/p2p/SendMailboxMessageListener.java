package io.bisq.p2p;

public interface SendMailboxMessageListener {
    void onArrived();

    void onStoredInMailbox();

    void onFault(String errorMessage);
}
