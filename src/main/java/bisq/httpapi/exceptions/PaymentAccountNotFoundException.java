package bisq.httpapi.exceptions;

public class PaymentAccountNotFoundException extends Exception {

    public PaymentAccountNotFoundException(String message) {
        super(message);
    }
}

