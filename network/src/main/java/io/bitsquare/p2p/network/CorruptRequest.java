package io.bitsquare.p2p.network;

public enum CorruptRequest {
    MaxSizeExceeded(1),
    InvalidDataType(0),
    WrongNetworkId(0),
    ViolatedThrottleLimit(1);

    public final int maxTolerance;

    CorruptRequest(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}
