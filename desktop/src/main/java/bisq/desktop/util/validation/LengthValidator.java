package bisq.desktop.util.validation;

import bisq.core.locale.Res;
import bisq.core.util.validation.InputValidator;

public class LengthValidator extends InputValidator {
    private int minLength;
    private int maxLength;

    public LengthValidator() {
        this(0, Integer.MAX_VALUE);
    }

    public LengthValidator(int min, int max) {
        this.minLength = min;
        this.maxLength = max;
    }

    @Override
    public ValidationResult validate(String input) {
        ValidationResult result = new ValidationResult(true);
        int length = (input == null) ? 0 : input.length();

        if (this.minLength == this.maxLength) {
            if (length != this.minLength)
                result = new ValidationResult(false, Res.get("validation.fixedLength", this.minLength));
        } else
        if (length < this.minLength || length > this.maxLength)
            result = new ValidationResult(false, Res.get("validation.length", this.minLength, this.maxLength));

        return result;
    }

    public void setMinLength(int minLength) {
        this.minLength = minLength;
    }

    public void setMaxLength(int maxLength) {
        this.maxLength = maxLength;
    }
}
