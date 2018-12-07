package bisq.httpapi.exceptions;

public class ExperimentalFeatureException extends RuntimeException {
    public ExperimentalFeatureException() {
        super("Experimental features disabled");
    }
}
