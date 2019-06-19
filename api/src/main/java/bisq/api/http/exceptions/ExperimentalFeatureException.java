package bisq.api.http.exceptions;

public class ExperimentalFeatureException extends RuntimeException {
    public ExperimentalFeatureException() {
        super("Experimental features disabled");
    }
}
