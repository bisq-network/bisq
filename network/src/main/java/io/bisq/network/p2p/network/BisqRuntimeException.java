package io.bisq.network.p2p.network;

class BisqRuntimeException extends RuntimeException {
    BisqRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
}
