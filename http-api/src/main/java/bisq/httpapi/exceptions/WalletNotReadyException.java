package bisq.httpapi.exceptions;

public class WalletNotReadyException extends RuntimeException {

    public WalletNotReadyException(String message) {
        super(message);
    }

}
