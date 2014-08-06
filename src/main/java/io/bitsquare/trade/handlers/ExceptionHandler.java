package io.bitsquare.trade.handlers;
/**
 * For reporting throwables only
 */
public interface ExceptionHandler
{
    void onError(Throwable throwable);
}
