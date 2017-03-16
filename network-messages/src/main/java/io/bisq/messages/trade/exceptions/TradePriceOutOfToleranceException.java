package io.bisq.messages.trade.exceptions;

public class TradePriceOutOfToleranceException extends Exception {
    public TradePriceOutOfToleranceException(String message) {
        super(message);
    }
}
