/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.components;

import bisq.desktop.util.validation.InputValidator;

import bisq.common.locale.Res;

import org.controlsfx.control.PopOver;

import javafx.stage.Window;

import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.effect.BlurType;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Effect;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import javafx.geometry.Insets;
import javafx.geometry.Point2D;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;

/**
 * TextField with validation support.
 * If validator is set it supports on focus out validation with that validator. If a more sophisticated validation is
 * needed the validationResultProperty can be used for applying validation result done by external validation.
 * In case the isValid property in validationResultProperty get set to false we display a red border and an error
 * message within the errorMessageDisplay placed on the right of the text field.
 * The errorMessageDisplay gets closed when the ValidatingTextField instance gets removed from the scene graph or when
 * hideErrorMessageDisplay() is called.
 * There can be only 1 errorMessageDisplays at a time we use static field for it.
 * The position is derived from the position of the textField itself or if set from the layoutReference node.
 */
//TODO There are some rare situation where it behaves buggy. Needs further investigation and improvements. Also
// consider replacement with controlsFX components.
public class InputTextField extends TextField {

    private final Effect invalidEffect = new DropShadow(BlurType.THREE_PASS_BOX, Color.RED, 4, 0.0, 0, 0);

    private final ObjectProperty<InputValidator.ValidationResult> validationResult = new SimpleObjectProperty<>
            (new InputValidator.ValidationResult(true));

    private static PopOver errorMessageDisplay;
    private Region layoutReference = this;

    public InputValidator getValidator() {
        return validator;
    }

    public void setValidator(InputValidator validator) {
        this.validator = validator;
    }

    private InputValidator validator;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void hideErrorMessageDisplay() {
        if (errorMessageDisplay != null)
            errorMessageDisplay.hide();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InputTextField() {
        super();

        validationResult.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                setEffect(newValue.isValid ? null : invalidEffect);

                if (newValue.isValid)
                    hideErrorMessageDisplay();
                else
                    applyErrorMessage(newValue);
            }
        });

        sceneProperty().addListener((ov, oldValue, newValue) -> {
            // we got removed from the scene so hide the popup (if open)
            if (newValue == null)
                hideErrorMessageDisplay();
        });

        focusedProperty().addListener((o, oldValue, newValue) -> {
            if (oldValue && !newValue && validator != null)
                validationResult.set(validator.validate(getText()));
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void resetValidation() {
        setEffect(null);
        hideErrorMessageDisplay();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param layoutReference The node used as reference for positioning. If not set explicitly the
     *                        ValidatingTextField instance is used.
     */
    public void setLayoutReference(Region layoutReference) {
        this.layoutReference = layoutReference;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObjectProperty<InputValidator.ValidationResult> validationResultProperty() {
        return validationResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyErrorMessage(InputValidator.ValidationResult validationResult) {
        if (errorMessageDisplay != null)
            errorMessageDisplay.hide();

        if (!validationResult.isValid) {
            createErrorPopOver(validationResult.errorMessage);
            if (getScene() != null)
                errorMessageDisplay.show(getScene().getWindow(), getErrorPopupPosition().getX(),
                        getErrorPopupPosition().getY());

            if (errorMessageDisplay != null)
                errorMessageDisplay.setDetached(false);
        }
    }

    private Point2D getErrorPopupPosition() {
        Window window = getScene().getWindow();
        Point2D point;
        point = layoutReference.localToScene(0, 0);
        double x = Math.floor(point.getX() + window.getX() + layoutReference.getWidth() + 20 - getPadding().getLeft() -
                getPadding().getRight());
        double y = Math.floor(point.getY() + window.getY() + getHeight() / 2 - getPadding().getTop() - getPadding()
                .getBottom());
        return new Point2D(x, y);
    }


    private static void createErrorPopOver(String errorMessage) {
        Label errorLabel = new AutoTooltipLabel(errorMessage);
        errorLabel.setId("validation-error");
        errorLabel.setPadding(new Insets(0, 10, 0, 10));
        errorLabel.setOnMouseClicked(e -> hideErrorMessageDisplay());

        errorMessageDisplay = new PopOver(errorLabel);
        errorMessageDisplay.setDetachable(true);
        errorMessageDisplay.setDetachedTitle(Res.get("shared.close"));
        errorMessageDisplay.setArrowIndent(5);
    }

}
