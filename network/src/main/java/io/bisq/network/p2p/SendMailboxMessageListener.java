package io.bisq.network.p2p;

public interface SendMailboxMessageListener {
    void onArrived();

    void onStoredInMailbox();

    void onFault(@SuppressWarnings("UnusedParameters") String errorMessage);
}
