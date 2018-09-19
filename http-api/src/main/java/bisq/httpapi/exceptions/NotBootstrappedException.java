package bisq.httpapi.exceptions;

public class NotBootstrappedException extends RuntimeException {
    public NotBootstrappedException() {
        super("P2P network is not ready yet.");
    }
}
