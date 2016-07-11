package io.bitsquare.trade.protocol.availability;

public enum AvailabilityResult {
    AVAILABLE,
    OFFER_TAKEN,
    PRICE_OUT_OF_TOLERANCE,
    NO_ARBITRATORS,
    USER_IGNORED,
    UNKNOWN_FAILURE
}
