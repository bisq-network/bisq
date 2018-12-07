package bisq.httpapi.exceptions;

public class InsufficientMoneyException extends Exception {

    public InsufficientMoneyException(String message) {
        super(message);
    }
}
