package bisq.desktop.components.controls.validation;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.control.TextInputControl;

/**
 * Drop-in replacement for {@code com.jfoenix.validation.base.ValidatorBase}.
 * Subclasses implement {@link #eval()} and consult {@link #srcControl} to inspect the
 * input. They set {@link #hasErrors} via {@code hasErrors.set(...)} to signal failure.
 *
 * The property-based API (message, hasErrors) mirrors jfoenix's so existing helpers
 * such as {@code JFXInputValidator} keep working unchanged.
 */
public abstract class Validator {

    protected final StringProperty message = new SimpleStringProperty();
    protected final BooleanProperty hasErrors = new SimpleBooleanProperty(false);
    protected TextInputControl srcControl;

    protected Validator() {}

    protected Validator(String message) {
        this.message.set(message);
    }

    /** Called by the owning input before evaluating; ensures subclasses see the right control. */
    public final void setSrcControl(TextInputControl control) {
        this.srcControl = control;
    }

    public final TextInputControl getSrcControl() {
        return srcControl;
    }

    /**
     * Subclass hook. Must set {@link #hasErrors} for BOTH outcomes: {@code true} when invalid,
     * {@code false} when valid — {@link #validate(TextInputControl)} does not pre-reset it, so a
     * one-sided implementation will get stuck in the previous state.
     *
     * <p>Validators that manage {@code hasErrors} externally (e.g. {@code JFXInputValidator},
     * which sets it via {@code applyErrorMessage(...)} / {@code resetValidation()} from the
     * surrounding controller) may leave {@code eval()} as a no-op.
     */
    protected abstract void eval();

    /** Invoked by the owning input. Runs {@link #eval()} after binding the control. */
    public final void validate(TextInputControl control) {
        setSrcControl(control);
        eval();
    }

    public final boolean getHasErrors() {
        return hasErrors.get();
    }

    public final BooleanProperty hasErrorsProperty() {
        return hasErrors;
    }

    public final String getMessage() {
        return message.get();
    }

    public final void setMessage(String m) {
        message.set(m);
    }

    public final StringProperty messageProperty() {
        return message;
    }
}
