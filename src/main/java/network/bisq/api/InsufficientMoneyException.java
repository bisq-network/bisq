package network.bisq.api;

public class InsufficientMoneyException extends Exception {

    public InsufficientMoneyException(String message) {
        super(message);
    }
}
