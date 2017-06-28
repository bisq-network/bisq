package io.bisq.network.p2p.network;

public enum RuleViolation {
    INVALID_DATA_TYPE(2),
    WRONG_NETWORK_ID(0),
    MAX_MSG_SIZE_EXCEEDED(2),
    THROTTLE_LIMIT_EXCEEDED(2),
    TOO_MANY_REPORTED_PEERS_SENT(2),
    PEER_BANNED(0),
    INVALID_CLASS(0);

    public final int maxTolerance;

    RuleViolation(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}
