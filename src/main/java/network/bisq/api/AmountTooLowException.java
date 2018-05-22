package network.bisq.api;

public class AmountTooLowException extends Exception {
    public AmountTooLowException(String message) {
        super(message);
    }
}
