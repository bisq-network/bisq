package network.bisq.httpapi;

public class InsufficientMoneyException extends Exception {

    public InsufficientMoneyException(String message) {
        super(message);
    }
}
