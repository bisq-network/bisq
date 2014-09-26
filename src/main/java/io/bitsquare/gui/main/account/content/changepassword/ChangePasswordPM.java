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

package io.bitsquare.gui.main.account.content.changepassword;

import io.bitsquare.gui.PresentationModel;
import io.bitsquare.gui.util.validation.InputValidator;
import io.bitsquare.gui.util.validation.PasswordValidator;

import com.google.inject.Inject;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ChangePasswordPM extends PresentationModel<ChangePasswordModel> {
    private static final Logger log = LoggerFactory.getLogger(ChangePasswordPM.class);

    private final PasswordValidator passwordValidator;

    private String errorMessage;

    final StringProperty passwordField = new SimpleStringProperty();
    final StringProperty repeatedPasswordField = new SimpleStringProperty();
    final BooleanProperty saveButtonDisabled = new SimpleBooleanProperty(true);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ChangePasswordPM(ChangePasswordModel model, PasswordValidator passwordValidator) {
        super(model);
        this.passwordValidator = passwordValidator;

        passwordField.addListener((ov) -> saveButtonDisabled.set(!validate()));
        repeatedPasswordField.addListener((ov) -> saveButtonDisabled.set(!validate()));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("EmptyMethod")
    @Override
    public void initialize() {
        super.initialize();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void activate() {
        super.activate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void deactivate() {
        super.deactivate();
    }

    @SuppressWarnings("EmptyMethod")
    @Override
    public void terminate() {
        super.terminate();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public
    ///////////////////////////////////////////////////////////////////////////////////////////

    boolean requestSavePassword() {
        if (validate()) {
            model.savePassword(passwordField.get());
            return true;
        }
        return false;
    }

    String getErrorMessage() {
        return errorMessage;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean validate() {
        InputValidator.ValidationResult result = passwordValidator.validate(passwordField.get());
        if (result.isValid) {
            result = passwordValidator.validate(repeatedPasswordField.get());
            if (result.isValid) {
                if (passwordField.get().equals(repeatedPasswordField.get()))
                    return true;
                else
                    errorMessage = "The 2 passwords does not match.";
            }
            else {
                errorMessage = result.errorMessage;
            }
        }
        else {
            errorMessage = result.errorMessage;
        }
        return false;
    }


}
