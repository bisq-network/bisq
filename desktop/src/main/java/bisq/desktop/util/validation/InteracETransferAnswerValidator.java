package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import javax.inject.Inject;

public class InteracETransferAnswerValidator extends InputValidator {
    private LengthValidator lengthValidator;
    private RegexValidator regexValidator;

    @Inject
    public InteracETransferAnswerValidator(LengthValidator lengthValidator, RegexValidator regexValidator) {

        lengthValidator.setMinLength(3);
        lengthValidator.setMaxLength(25);
        this.lengthValidator = lengthValidator;

        regexValidator.setPattern("[A-Za-z0-9\\-]+");
        regexValidator.setErrorMessage(Res.get("validation.interacETransfer.invalidAnswer"));
        this.regexValidator = regexValidator;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = super.validate(input);

        if (result.isValid)
            result = lengthValidator.validate(input);
        if (result.isValid)
            result = regexValidator.validate(input);

        return result;
    }
}
