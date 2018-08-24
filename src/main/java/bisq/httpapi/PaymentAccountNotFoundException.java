package bisq.httpapi;

public class PaymentAccountNotFoundException extends Exception {

    public PaymentAccountNotFoundException(String message) {
        super(message);
    }
}

