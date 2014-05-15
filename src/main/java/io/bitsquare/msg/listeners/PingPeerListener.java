package io.bitsquare.msg.listeners;

public interface PingPeerListener
{
    void onPing();

    void onPingPeerResult(boolean success);
}
