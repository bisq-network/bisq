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

import io.bitsquare.gui.View;
import io.bitsquare.gui.main.account.MultiStepNavigation;
import io.bitsquare.gui.main.account.content.ContextAware;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChangePasswordView extends View<ChangePasswordViewModel> implements ContextAware {

    private static final Logger log = LoggerFactory.getLogger(ChangePasswordView.class);

    @FXML HBox buttonsHBox;
    @FXML Button saveButton, skipButton;
    @FXML PasswordField oldPasswordField, passwordField, repeatedPasswordField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    private ChangePasswordView(ChangePasswordViewModel model) {
        super(model);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        passwordField.textProperty().bindBidirectional(model.passwordField);
        repeatedPasswordField.textProperty().bindBidirectional(model.repeatedPasswordField);

        saveButton.disableProperty().bind(model.saveButtonDisabled);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ContextAware implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void useSettingsContext(boolean useSettingsContext) {
        if (useSettingsContext)
            buttonsHBox.getChildren().remove(skipButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onSaved() {
        boolean result = model.requestSavePassword();
        if (result) {
            if (parent instanceof MultiStepNavigation)
                ((MultiStepNavigation) parent).nextStep(this);
        }
        else {
            log.debug(model.getErrorMessage()); // TODO use validating TF
        }
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.SETUP_PASSWORD);
    }

    @FXML
    private void onSkipped() {
        if (parent instanceof MultiStepNavigation)
            ((MultiStepNavigation) parent).nextStep(this);
    }

}

