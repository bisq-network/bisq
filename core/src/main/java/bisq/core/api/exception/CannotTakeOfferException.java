package bisq.core.api.exception;

import bisq.common.BisqException;

public class CannotTakeOfferException extends BisqException {

    public CannotTakeOfferException(String format, Object... args) {
        super(format, args);
    }
}
