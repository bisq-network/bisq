package io.bitsquare.p2p.network;

public enum CloseConnectionReason {
    // First block are from different exceptions
    SOCKET_CLOSED(false),
    RESET(false),
    SOCKET_TIMEOUT(false),
    TERMINATED(false), // EOFException
    UNKNOWN_EXCEPTION(false),

    // Planned
    APP_SHUT_DOWN(true),
    CLOSE_REQUESTED_BY_PEER(false),

    // send msg
    SEND_MSG_FAILURE(false),
    SEND_MSG_TIMEOUT(false),

    // maintenance
    TOO_MANY_CONNECTIONS_OPEN(true),
    TOO_MANY_SEED_NODES_CONNECTED(true),

    // illegal requests
    RULE_VIOLATION(true);

    public final boolean sendCloseMessage;

    CloseConnectionReason(boolean sendCloseMessage) {
        this.sendCloseMessage = sendCloseMessage;
    }
}
