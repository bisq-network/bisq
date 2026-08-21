package bisq.desktop.components.controls.validation;

/**
 * Drop-in replacement for {@code com.jfoenix.validation.RequiredFieldValidator}.
 * Fails if the bound text input is null or empty.
 */
public class RequiredFieldValidator extends Validator {

    public RequiredFieldValidator() {
        super("Required field");
    }

    public RequiredFieldValidator(String message) {
        super(message);
    }

    @Override
    protected void eval() {
        if (srcControl == null) {
            hasErrors.set(false);
            return;
        }
        String text = srcControl.getText();
        hasErrors.set(text == null || text.isEmpty());
    }
}
