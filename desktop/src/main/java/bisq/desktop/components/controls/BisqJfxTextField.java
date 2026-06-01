package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqTextFieldSkin;
import bisq.desktop.components.controls.validation.Validator;

import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.scene.control.TextField;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXTextField}.
 *
 * Keeps the same CSS surface (style class {@code jfx-text-field}) so the existing
 * stylesheets in {@code bisq.css} / {@code theme-*.css} match without modification.
 * Re-exposes the subset of the jfoenix API used by the codebase:
 * {@code getValidators()}, {@code setValidators(...)}, {@code validate()},
 * {@code setLabelFloat(boolean)} / {@code isLabelFloat()}.
 */
public class BisqJfxTextField extends TextField implements LabelFloatable {

    private final InputControlBehaviour behaviour = new InputControlBehaviour(this);

    public BisqJfxTextField() {
        super();
        getStyleClass().add("jfx-text-field");
    }

    public BisqJfxTextField(String text) {
        super(text);
        getStyleClass().add("jfx-text-field");
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
