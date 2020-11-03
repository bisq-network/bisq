package bisq.core.util.validation;

import bisq.core.locale.Res;

public class RegexValidator extends InputValidator {
    private String pattern;
    private String errorMessage;

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = new ValidationResult(true);
        String message = (this.errorMessage == null) ? Res.get("validation.pattern", this.pattern) : this.errorMessage;
        String testStr = input == null ? "" : input;

        if (this.pattern == null)
            return result;

        if (!testStr.matches(this.pattern))
            result = new ValidationResult(false, message);

        return result;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
