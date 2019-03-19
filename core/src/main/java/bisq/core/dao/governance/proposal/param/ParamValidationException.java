package bisq.core.dao.governance.proposal.param;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class ParamValidationException extends Exception {
    @Nullable
    private ChangeParamValidator.Result result;

    ParamValidationException(ChangeParamValidator.Result result) {
        super(result.getErrorMsg());
        this.result = result;
    }

    ParamValidationException(Throwable throwable) {
        super(throwable.getMessage());
        initCause(throwable);
    }

    @Override
    public String toString() {
        return "ParamValidationException{" +
                "\n     result=" + result +
                "\n} " + super.toString();
    }
}
