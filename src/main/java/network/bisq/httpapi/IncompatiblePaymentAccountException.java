package network.bisq.httpapi;

public class IncompatiblePaymentAccountException extends Exception {

    public IncompatiblePaymentAccountException(String message) {
        super(message);
    }
}
