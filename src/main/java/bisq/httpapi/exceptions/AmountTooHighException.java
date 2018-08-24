package bisq.httpapi.exceptions;

public class AmountTooHighException extends Exception {
    public AmountTooHighException(String message) {
        super(message);
    }
}
