package io.bisq.network_messages.trade.exceptions;

public class MarketPriceNotAvailableException extends Exception {
    public MarketPriceNotAvailableException(String message) {
        super(message);
    }
}
