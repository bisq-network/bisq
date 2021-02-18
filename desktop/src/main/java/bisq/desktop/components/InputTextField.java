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


import bisq.desktop.util.validation.JFXInputValidator;

import bisq.core.util.validation.InputValidator;

import com.jfoenix.controls.JFXTextField;

import javafx.scene.control.Skin;

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
//TODO There are some rare situation where it behaves buggy. Needs further investigation and improvements.
public class InputTextField extends JFXTextField {

    private final ObjectProperty<InputValidator.ValidationResult> validationResult = new SimpleObjectProperty<>
            (new InputValidator.ValidationResult(true));

    private final JFXInputValidator jfxValidationWrapper = new JFXInputValidator();
    private double inputLineExtension = 0;

    private InputValidator validator;
    private String errorMessage = null;


    public InputValidator getValidator() {
        return validator;
    }

    public void setValidator(InputValidator validator) {
        this.validator = validator;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public InputTextField() {
        super();

        getValidators().add(jfxValidationWrapper);

        validationResult.addListener((ov, oldValue, newValue) -> {
            if (newValue != null) {
                jfxValidationWrapper.resetValidation();
                if (!newValue.isValid) {
                    if (!newValue.errorMessageEquals(oldValue)) {  // avoid blinking
                        validate();  // ensure that the new error message replaces the old one
                    }
                    if (this.errorMessage != null) {
                        jfxValidationWrapper.applyErrorMessage(this.errorMessage);
                    } else {
                        jfxValidationWrapper.applyErrorMessage(newValue);
                    }
                }
                validate();
            }
        });

        textProperty().addListener((o, oldValue, newValue) -> {
            refreshValidation();
        });

        focusedProperty().addListener((o, oldValue, newValue) -> {
            if (validator != null) {
                if (!oldValue && newValue) {
                    this.validationResult.set(new InputValidator.ValidationResult(true));
                } else {
                    this.validationResult.set(validator.validate(getText()));
                }
            }
        });
    }


    public InputTextField(double inputLineExtension) {
        this();
        this.inputLineExtension = inputLineExtension;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void resetValidation() {
        jfxValidationWrapper.resetValidation();

        String input = getText();
        if (input.isEmpty()) {
            validationResult.set(new InputValidator.ValidationResult(true));
        } else {
            validationResult.set(validator.validate(input));
        }
    }

    public void refreshValidation() {
        if (validator != null) {
            this.validationResult.set(validator.validate(getText()));
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ObjectProperty<InputValidator.ValidationResult> validationResultProperty() {
        return validationResult;
    }

    protected Skin<?> createDefaultSkin() {
        return new JFXTextFieldSkinBisqStyle<>(this, inputLineExtension);
    }
}
