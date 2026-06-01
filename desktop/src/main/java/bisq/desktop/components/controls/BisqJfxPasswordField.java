package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqTextFieldSkin;
import bisq.desktop.components.controls.validation.Validator;

import javafx.collections.ObservableList;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Skin;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXPasswordField}.
 * Same CSS surface ({@code .jfx-password-field}), same jfoenix-style API subset.
 */
public class BisqJfxPasswordField extends PasswordField implements LabelFloatable {

    private final InputControlBehaviour behaviour = new InputControlBehaviour(this);

    public BisqJfxPasswordField() {
        super();
        getStyleClass().add("jfx-password-field");
    }

    public ObservableList<Validator> getValidators() {
        return behaviour.getValidators();
    }

    public void setValidators(Validator... validators) {
        behaviour.setValidators(validators);
    }

    public boolean validate() {
        return behaviour.validate();
    }

    public boolean isLabelFloat() {
        return behaviour.isLabelFloat();
    }

    public void setLabelFloat(boolean v) {
        behaviour.setLabelFloat(v);
    }

    public javafx.beans.property.BooleanProperty labelFloatProperty() {
        return behaviour.labelFloatProperty();
    }

    public javafx.beans.property.ReadOnlyStringProperty errorMessageProperty() {
        return behaviour.errorMessageProperty();
    }

    @Override
    protected Skin<?> createDefaultSkin() {
        return new BisqTextFieldSkin(this);
    }
}
