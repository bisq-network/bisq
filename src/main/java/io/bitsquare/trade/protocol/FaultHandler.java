package io.bitsquare.trade.protocol;

public interface FaultHandler
{
    void onFault(Throwable throwable);
}
