package bisq.updater.linux.pkexec;

public class PkexecAuthorizationFailedException extends RuntimeException {
    public PkexecAuthorizationFailedException(String message) {
        super(message);
    }
}
