package io.bisq.messages.availability;

public enum AvailabilityResult {
    UNKNOWN_FAILURE,
    AVAILABLE,
    OFFER_TAKEN,
    PRICE_OUT_OF_TOLERANCE,
    MARKET_PRICE_NOT_AVAILABLE,
    NO_ARBITRATORS,
    USER_IGNORED
}
