package io.bitsquare.trade.protocol.availability;

public enum AvailabilityResult {
    AVAILABLE,
    OFFER_TAKEN,
    PRICE_OUT_OF_TOLERANCE,
    MARKET_PRICE_NOT_AVAILABLE,
    NO_ARBITRATORS,
    USER_IGNORED,
    UNKNOWN_FAILURE
}
