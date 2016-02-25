package io.bitsquare.p2p.network;

public enum CloseConnectionReason {
    // First block are from different exceptions
    SOCKET_CLOSED(false),
    RESET(false),
    SOCKET_TIMEOUT(false),
    TERMINATED(false), // EOFException
    UNKNOWN_EXCEPTION(false),

    // Planned
    APP_SHUT_DOWN(true, true),
    CLOSE_REQUESTED_BY_PEER(false, true),

    // send msg
    SEND_MSG_FAILURE(false),
    SEND_MSG_TIMEOUT(false),

    // maintenance
    TOO_MANY_CONNECTIONS_OPEN(true, true),
    TOO_MANY_SEED_NODES_CONNECTED(true, true),
    UNKNOWN_PEER_ADDRESS(true, true),

    // illegal requests
    RULE_VIOLATION(true, true);

    public final boolean sendCloseMessage;
    public boolean isIntended;

    CloseConnectionReason(boolean sendCloseMessage) {
        this(sendCloseMessage, true);
    }

    CloseConnectionReason(boolean sendCloseMessage, boolean isIntended) {
        this.sendCloseMessage = sendCloseMessage;
        this.isIntended = isIntended;
    }
}
