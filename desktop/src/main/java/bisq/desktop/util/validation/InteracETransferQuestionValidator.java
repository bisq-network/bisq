package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import javax.inject.Inject;

public class InteracETransferQuestionValidator extends InputValidator {
    private LengthValidator lengthValidator;
    private RegexValidator regexValidator;

    @Inject
    public InteracETransferQuestionValidator(LengthValidator lengthValidator, RegexValidator regexValidator) {

        lengthValidator.setMinLength(1);
        lengthValidator.setMaxLength(40);
        this.lengthValidator = lengthValidator;

        regexValidator.setPattern("[A-Za-z0-9\\-\\_\\'\\,\\.\\? ]+");
        regexValidator.setErrorMessage(Res.get("validation.interacETransfer.invalidQuestion"));
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
