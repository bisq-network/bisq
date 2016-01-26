package io.bitsquare.p2p.network;

public enum IllegalRequest {
    MaxSizeExceeded(1),
    InvalidDataType(0),
    WrongNetworkId(0);

    public final int maxTolerance;

    IllegalRequest(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}
