/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components;

import io.bitsquare.gui.util.validation.InputValidator;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.control.*;
import javafx.scene.effect.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.Window;

import org.controlsfx.control.PopOver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * TextField with validation support.
 * In case the isValid property in amountValidationResultProperty get set to false we display a red border and an error
 * message within the errorMessageDisplay placed on the right of the text field.
 * The errorMessageDisplay gets closed when the ValidatingTextField instance gets removed from the scene graph or when
 * hideErrorMessageDisplay() is called.
 * There can be only 1 errorMessageDisplays at a time we use static field for it.
 * The position is derived from the position of the textField itself or if set from the layoutReference node.
 */
public class ValidatingTextField extends TextField {
    private static final Logger log = LoggerFactory.getLogger(ValidatingTextField.class);

    private Effect invalidEffect = new DropShadow(BlurType.GAUSSIAN, Color.RED, 4, 0.0, 0, 0);

    final ObjectProperty<InputValidator.ValidationResult> amountValidationResult = new SimpleObjectProperty<>(new
            InputValidator.ValidationResult(true));

    private static PopOver errorMessageDisplay;
    private Region layoutReference = this;


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

    public ValidatingTextField() {
        super();

        amountValidationResult.addListener((ov, oldValue, newValue) -> {
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

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * @param layoutReference The node used as reference for positioning. If not set explicitely the
     *                        ValidatingTextField instance is used.
     */
    public void setLayoutReference(Region layoutReference) {
        this.layoutReference = layoutReference;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObjectProperty<InputValidator.ValidationResult> amountValidationResultProperty() {
        return amountValidationResult;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void applyErrorMessage(InputValidator.ValidationResult validationResult) {
        if (validationResult.isValid) {
            if (errorMessageDisplay != null) {
                errorMessageDisplay.hide();
            }
        }
        else {
            if (errorMessageDisplay == null)
                createErrorPopOver(validationResult.errorMessage);
            else
                ((Label) errorMessageDisplay.getContentNode()).setText(validationResult.errorMessage);

            errorMessageDisplay.show(getScene().getWindow(), getErrorPopupPosition().getX(),
                    getErrorPopupPosition().getY());
        }
    }

    private Point2D getErrorPopupPosition() {
        Window window = getScene().getWindow();
        Point2D point;
        point = layoutReference.localToScene(0, 0);
        double x = point.getX() + window.getX() + layoutReference.getWidth() + 20;
        double y = point.getY() + window.getY() + Math.floor(getHeight() / 2);
        return new Point2D(x, y);
    }


    private static void createErrorPopOver(String errorMessage) {
        Label errorLabel = new Label(errorMessage);
        errorLabel.setId("validation-error");
        errorLabel.setPadding(new Insets(0, 10, 0, 10));

        ValidatingTextField.errorMessageDisplay = new PopOver(errorLabel);
        ValidatingTextField.errorMessageDisplay.setDetachable(false);
        ValidatingTextField.errorMessageDisplay.setArrowIndent(5);
    }

}