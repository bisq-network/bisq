package io.bitsquare.trade.handlers;

/**
 * For reporting error message only (UI)
 */
public interface ErrorMessageHandler
{
    void onFault(String errorMessage);
}
