package io.bisq.network_messages.trade.exceptions;

public class TradePriceOutOfToleranceException extends Exception {
    public TradePriceOutOfToleranceException(String message) {
        super(message);
    }
}
