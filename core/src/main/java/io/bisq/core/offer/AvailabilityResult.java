package io.bisq.core.offer;

public enum AvailabilityResult {
    UNKNOWN_FAILURE,
    AVAILABLE,
    OFFER_TAKEN,
    PRICE_OUT_OF_TOLERANCE,
    MARKET_PRICE_NOT_AVAILABLE,
    NO_ARBITRATORS,
    NO_MEDIATORS,
    USER_IGNORED
}