package bisq.httpapi;

public class WalletNotReadyException extends RuntimeException {

    public WalletNotReadyException(String message) {
        super(message);
    }

}
