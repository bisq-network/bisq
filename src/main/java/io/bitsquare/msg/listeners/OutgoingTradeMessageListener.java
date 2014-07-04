package io.bitsquare.msg.listeners;

public interface OutgoingTradeMessageListener
{
    void onFailed();

    void onResult();
}
