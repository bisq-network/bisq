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

package io.bitsquare.gui.account.changepassword;

import io.bitsquare.gui.CachedCodeBehind;
import io.bitsquare.gui.account.settings.AccountSettingsCB;
import io.bitsquare.gui.account.setup.SetupCB;
import io.bitsquare.gui.help.Help;
import io.bitsquare.gui.help.HelpId;

import java.net.URL;

import java.util.ResourceBundle;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PasswordCB extends CachedCodeBehind<PasswordPM> {

    private static final Logger log = LoggerFactory.getLogger(PasswordCB.class);

    @FXML private HBox buttonsHBox;
    @FXML private Button saveButton, skipButton;
    @FXML private PasswordField passwordField, repeatedPasswordField;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public PasswordCB(PasswordPM presentationModel) {
        super(presentationModel);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Lifecycle
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void initialize(URL url, ResourceBundle rb) {
        super.initialize(url, rb);

        passwordField.textProperty().bindBidirectional(presentationModel.passwordField);
        repeatedPasswordField.textProperty().bindBidirectional(presentationModel.repeatedPasswordField);

        saveButton.disableProperty().bind(presentationModel.saveButtonDisabled);
    }

    @Override
    public void activate() {
        super.activate();
    }

    @Override
    public void deactivate() {
        super.deactivate();
    }

    @Override
    public void terminate() {
        super.terminate();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Override from CodeBehind
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void setParentController(Initializable parentController) {
        super.setParentController(parentController);
        if (parentController instanceof AccountSettingsCB) {
            buttonsHBox.getChildren().remove(skipButton);
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // UI handlers
    ///////////////////////////////////////////////////////////////////////////////////////////

    @FXML
    private void onSaved() {
        boolean result = presentationModel.savePassword();
        if (result) {
            if (parentController instanceof SetupCB)
                ((SetupCB) parentController).onCompleted(this);
        }
        else {
            log.debug(presentationModel.errorMessage); // TODO use validating TF
        }
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.SETUP_PASSWORD);
    }

    public void onSkipped() {
        if (parentController instanceof SetupCB)
            ((SetupCB) parentController).onCompleted(this);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private methods
    ///////////////////////////////////////////////////////////////////////////////////////////


}

