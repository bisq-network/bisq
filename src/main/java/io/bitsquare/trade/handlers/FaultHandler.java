package io.bitsquare.trade.handlers;

/**
 * For reporting a description message and throwable
 */
public interface FaultHandler
{
    void onFault(String message, Throwable throwable);
}
