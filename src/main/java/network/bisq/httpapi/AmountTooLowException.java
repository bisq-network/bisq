package network.bisq.httpapi;

public class AmountTooLowException extends Exception {
    public AmountTooLowException(String message) {
        super(message);
    }
}
