package bisq.desktop.components.controls;

import bisq.desktop.components.controls.skin.BisqTextAreaSkin;
import bisq.desktop.components.controls.validation.Validator;

import javafx.collections.ObservableList;
import javafx.scene.control.Skin;
import javafx.scene.control.TextArea;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXTextArea}.
 * Same CSS surface ({@code .jfx-text-area}), same jfoenix-style API subset.
 */
public class BisqJfxTextArea extends TextArea implements LabelFloatable {

    private final InputControlBehaviour behaviour = new InputControlBehaviour(this);

    public BisqJfxTextArea() {
        super();
        getStyleClass().add("jfx-text-area");
    }

    public BisqJfxTextArea(String text) {
        super(text);
        getStyleClass().add("jfx-text-area");
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
        return new BisqTextAreaSkin(this);
    }
}
