package io.bitsquare.p2p;

class NetworkNotReadyException extends RuntimeException {

    public NetworkNotReadyException() {
        super("You must have bootstrapped before adding data to the P2P network.");
    }

}
