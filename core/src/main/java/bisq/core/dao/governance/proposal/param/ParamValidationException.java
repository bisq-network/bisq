package bisq.core.dao.governance.proposal.param;

import lombok.Getter;

import javax.annotation.Nullable;

@Getter
public class ParamValidationException extends Exception {
    @Nullable
    private ChangeParamValidator.Result result;

    public ParamValidationException(ChangeParamValidator.Result result) {
        this.result = result;
    }

    public ParamValidationException(Throwable throwable) {
        super(throwable);
    }
}
