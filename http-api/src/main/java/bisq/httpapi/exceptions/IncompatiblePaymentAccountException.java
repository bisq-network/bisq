package bisq.httpapi.exceptions;

public class IncompatiblePaymentAccountException extends Exception {

    public IncompatiblePaymentAccountException(String message) {
        super(message);
    }
}
