package bisq.core.dao.governance.proposal.param;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class
ParamValidationException extends Exception {
    enum ERROR {
        SAME,
        NO_CHANGE_POSSIBLE,
        TOO_LOW,
        TOO_HIGH
    }

    @Nullable
    private ParamValidationException.ERROR error;


    ParamValidationException(ParamValidationException.ERROR error, String errorMessage) {
        super(errorMessage);
        this.error = error;
    }

    ParamValidationException(Throwable throwable) {
        super(throwable.getMessage());
        initCause(throwable);
    }

    ParamValidationException(String errorMessage) {
        super(errorMessage);
    }

    @Override
    public String toString() {
        return "ParamValidationException{" +
                "\n     error=" + error +
                "\n} " + super.toString();
    }
}
