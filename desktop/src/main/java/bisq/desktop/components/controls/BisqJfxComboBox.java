package bisq.desktop.components.controls;

import bisq.desktop.components.controls.validation.Validator;

import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextInputControl;

/**
 * Drop-in replacement for {@code com.jfoenix.controls.JFXComboBox}.
 * Re-exposes only the API surface bisq uses; visuals match via the
 * {@code .jfx-combo-box} style class.
 */
public class BisqJfxComboBox<T> extends ComboBox<T> implements LabelFloatable {

    public BisqJfxComboBox() {
        super();
        getStyleClass().add("jfx-combo-box");
    }

    public BisqJfxComboBox(ObservableList<T> items) {
        super(items);
        getStyleClass().add("jfx-combo-box");
    }

    @Override
    protected javafx.scene.control.Skin<?> createDefaultSkin() {
        return new bisq.desktop.components.controls.skin.BisqComboBoxSkin<>(this);
    }

    // ----- Validation (mirrors jfoenix JFXComboBox.getValidators()/validate()) ------------------
    // ComboBox is not a TextInputControl, so validators run against the editor (present on
    // editable combos). Controller-driven validators that set hasErrors externally still work for
    // non-editable combos. validate() toggles the ":error" pseudo-class (styled in theme-*.css)
    // and publishes the first failing message via errorMessageProperty() for the skin's error label.
    private static final PseudoClass ERROR_PC = PseudoClass.getPseudoClass("error");
    private final ObservableList<Validator> validators = FXCollections.observableArrayList();
    private final ReadOnlyStringWrapper errorMessage = new ReadOnlyStringWrapper("");

    public ObservableList<Validator> getValidators() {
        return validators;
    }

    public void setValidators(Validator... vs) {
        validators.setAll(vs);
    }

    public boolean validate() {
        TextInputControl input = isEditable() ? getEditor() : null;
        boolean anyError = false;
        String msg = null;
        for (Validator v : validators) {
            if (input != null) {
                v.validate(input);
            }
            if (v.getHasErrors()) {
                anyError = true;
                if (msg == null) {
                    String m = v.getMessage();
                    if (m != null && !m.isEmpty()) {
                        msg = m;
                    }
                }
            }
        }
        pseudoClassStateChanged(ERROR_PC, anyError);
        errorMessage.set(msg);
        return !anyError;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage.getReadOnlyProperty();
    }

    // ----- jfoenix API surface bisq uses on its combo box --------------------------------------
    // Floating label flag is informational here; the visual lift happens via CSS pseudo-class.
    // Pseudo-class driven from a property listener so direct property mutations
    // (FXML, bindings, labelFloatProperty().set(...)) stay consistent with setLabelFloat().
    private static final javafx.css.PseudoClass LABEL_FLOAT_PC =
            javafx.css.PseudoClass.getPseudoClass("label-float");
    private final javafx.beans.property.BooleanProperty labelFloat =
            new javafx.beans.property.SimpleBooleanProperty(false) {
                @Override
                protected void invalidated() {
                    pseudoClassStateChanged(LABEL_FLOAT_PC, get());
                }
            };

    public boolean isLabelFloat() { return labelFloat.get(); }

    public void setLabelFloat(boolean v) {
        labelFloat.set(v);
    }

    public javafx.beans.property.BooleanProperty labelFloatProperty() { return labelFloat; }
}
