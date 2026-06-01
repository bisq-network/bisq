package bisq.desktop.components.controls;

import bisq.desktop.components.controls.validation.Validator;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.TextInputControl;

/**
 * Shared behaviour that the JFX-style text/password/area subclasses delegate to. Validators
 * are bisq-native {@link Validator} instances. Aggregates the first failing validator's
 * message into a single {@link #errorMessageProperty()} the skin can render below the input.
 */
public final class InputControlBehaviour {

    private static final PseudoClass ERROR = PseudoClass.getPseudoClass("error");

    private final TextInputControl owner;
    private final ObservableList<Validator> validators = FXCollections.observableArrayList();
    private static final PseudoClass LABEL_FLOAT = PseudoClass.getPseudoClass("label-float");
    private final BooleanProperty labelFloat;
    private final SimpleStringProperty errorMessage = new SimpleStringProperty();
    private final ChangeListener<Boolean> hasErrorsListener = (o, ov, nv) -> recomputeError();
    private final ChangeListener<String> messageListener = (o, ov, nv) -> recomputeError();

    public InputControlBehaviour(TextInputControl owner) {
        this.owner = owner;
        // Pseudo-class driven from the property so direct mutations (FXML, bindings,
        // labelFloatProperty().set(...)) stay consistent with setLabelFloat().
        labelFloat = new SimpleBooleanProperty(false) {
            @Override
            protected void invalidated() {
                ((Node) owner).pseudoClassStateChanged(LABEL_FLOAT, get());
            }
        };
        validators.addListener((ListChangeListener<Validator>) c -> {
            while (c.next()) {
                for (Validator v : c.getRemoved()) {
                    v.hasErrorsProperty().removeListener(hasErrorsListener);
                    v.messageProperty().removeListener(messageListener);
                }
                for (Validator v : c.getAddedSubList()) {
                    v.hasErrorsProperty().addListener(hasErrorsListener);
                    v.messageProperty().addListener(messageListener);
                }
            }
            recomputeError();
        });
    }

    public ObservableList<Validator> getValidators() {
        return validators;
    }

    public void setValidators(Validator... vs) {
        validators.setAll(vs);
    }

    /** Returns {@code true} when every validator passes. Toggles {@code :error} on the owner. */
    public boolean validate() {
        boolean anyError = false;
        for (Validator v : validators) {
            v.validate(owner);
            if (v.getHasErrors()) {
                anyError = true;
            }
        }
        ((Node) owner).pseudoClassStateChanged(ERROR, anyError);
        recomputeError();
        return !anyError;
    }

    public boolean isLabelFloat() {
        return labelFloat.get();
    }

    public void setLabelFloat(boolean v) {
        labelFloat.set(v);
    }

    public BooleanProperty labelFloatProperty() {
        return labelFloat;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage;
    }

    private void recomputeError() {
        String msg = null;
        for (Validator v : validators) {
            if (v.getHasErrors()) {
                msg = v.getMessage();
                if (msg != null && !msg.isEmpty()) break;
            }
        }
        errorMessage.set(msg);
    }
}
