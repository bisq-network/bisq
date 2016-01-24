package io.bitsquare.p2p;

public class NetworkNotReadyException extends RuntimeException {

    public NetworkNotReadyException() {
        super("You must have bootstrapped before adding data to the P2P network.");
    }

}
