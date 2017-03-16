package io.bisq.trade.exceptions;

public class TradePriceOutOfToleranceException extends Exception {
    public TradePriceOutOfToleranceException(String message) {
        super(message);
    }
}
