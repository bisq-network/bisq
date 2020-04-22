package bisq.cli.app;

public class BisqException extends RuntimeException {

    public BisqException(Throwable cause) {
        super(cause);
    }

    public BisqException(String format, Object... args) {
        super(String.format(format, args));
    }

    public BisqException(Throwable cause, String format, Object... args) {
        super(String.format(format, args), cause);
    }
}
