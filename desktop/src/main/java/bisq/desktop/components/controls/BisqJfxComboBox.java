package bisq.desktop.components.controls;

import javafx.collections.ObservableList;
import javafx.scene.control.ComboBox;

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
