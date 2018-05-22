package network.bisq.api;

public class WalletNotReadyException extends RuntimeException {

    public WalletNotReadyException(String message) {
        super(message);
    }

}
