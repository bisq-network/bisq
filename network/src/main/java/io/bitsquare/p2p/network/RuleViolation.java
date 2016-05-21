package io.bitsquare.p2p.network;

public enum RuleViolation {
    INVALID_DATA_TYPE(0),
    WRONG_NETWORK_ID(0),
    MAX_MSG_SIZE_EXCEEDED(2),
    THROTTLE_LIMIT_EXCEEDED(1),
    TOO_MANY_REPORTED_PEERS_SENT(1);

    public final int maxTolerance;

    RuleViolation(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}
