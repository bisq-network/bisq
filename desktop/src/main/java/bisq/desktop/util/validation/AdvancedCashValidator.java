package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import javax.inject.Inject;

public class AdvancedCashValidator extends InputValidator {
    private EmailValidator emailValidator;
    private RegexValidator regexValidator;

    @Inject
    public AdvancedCashValidator(EmailValidator emailValidator, RegexValidator regexValidator) {

        this.emailValidator = emailValidator;

        regexValidator.setPattern("[A-Za-z]{1}\\d{12}");
        regexValidator.setErrorMessage(Res.get("validation.advancedCash.invalidFormat"));
        this.regexValidator = regexValidator;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = super.validate(input);

        if (!result.isValid)
            return result;

        result = emailValidator.validate(input);

        if (!result.isValid)
            result = regexValidator.validate(input);

        return result;
    }
}
