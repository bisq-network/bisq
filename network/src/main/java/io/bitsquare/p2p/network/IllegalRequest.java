package io.bitsquare.p2p.network;

public enum IllegalRequest {
    // TODO check for needed allowed tolerance
    MaxSizeExceeded(1),
    NotAuthenticated(1),
    InvalidDataType(1),
    WrongNetworkId(1);

    public final int maxTolerance;

    IllegalRequest(int maxTolerance) {
        this.maxTolerance = maxTolerance;
    }
}
