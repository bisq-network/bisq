package bisq.updater;

public class InstallationFailedException extends RuntimeException {
    public InstallationFailedException(String message) {
        super(message);
    }

    public InstallationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
