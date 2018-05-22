package network.bisq.api;

public class IncompatiblePaymentAccountException extends Exception {

    public IncompatiblePaymentAccountException(String message) {
        super(message);
    }
}
