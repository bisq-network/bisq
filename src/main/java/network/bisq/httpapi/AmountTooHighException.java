package network.bisq.httpapi;

public class AmountTooHighException extends Exception {
    public AmountTooHighException(String message) {
        super(message);
    }
}
