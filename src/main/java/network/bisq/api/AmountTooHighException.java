package network.bisq.api;

public class AmountTooHighException extends Exception {
    public AmountTooHighException(String message) {
        super(message);
    }
}
