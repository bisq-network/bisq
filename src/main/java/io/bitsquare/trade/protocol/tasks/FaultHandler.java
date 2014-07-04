package io.bitsquare.trade.protocol.tasks;

public interface FaultHandler
{
    void onFault(Throwable throwable);
}
