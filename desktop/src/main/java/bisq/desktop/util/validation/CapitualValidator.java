package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;
import bisq.core.util.validation.RegexValidator;

import javax.inject.Inject;

public class CapitualValidator extends InputValidator {
    private final RegexValidator regexValidator;

    @Inject
    public CapitualValidator(RegexValidator regexValidator) {
        regexValidator.setPattern("CAP-[A-Za-z0-9]{6}");
        regexValidator.setErrorMessage(Res.get("validation.capitual.invalidFormat"));
        this.regexValidator = regexValidator;
    }

    @Override
    public ValidationResult validate(String input) {

        return regexValidator.validate(input);
    }
}
