package io.bitsquare.p2p.network;

public enum ConnectionPriority {
    PASSIVE,        // for connections initiated by other peer
    ACTIVE,         // for connections initiated by us
    DIRECT_MSG,     // for connections used for direct messaging
    AUTH_REQUEST    // for connections used for starting the authentication
}
