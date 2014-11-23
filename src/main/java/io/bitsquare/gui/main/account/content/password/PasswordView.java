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

package io.bitsquare.gui.main.account.content.password;

import io.bitsquare.gui.InitializableView;
import io.bitsquare.gui.Wizard;
import io.bitsquare.gui.main.help.Help;
import io.bitsquare.gui.main.help.HelpId;

import javax.inject.Inject;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;

public class PasswordView extends InitializableView<GridPane, PasswordViewModel> implements Wizard.Step {

    @FXML HBox buttonsHBox;
    @FXML Button saveButton, skipButton;
    @FXML PasswordField oldPasswordField, passwordField, repeatedPasswordField;

    private Wizard parent;

    @Inject
    private PasswordView(PasswordViewModel model) {
        super(model);
    }

    @Override
    public void initialize() {
        passwordField.textProperty().bindBidirectional(model.passwordField);
        repeatedPasswordField.textProperty().bindBidirectional(model.repeatedPasswordField);

        saveButton.disableProperty().bind(model.saveButtonDisabled);
    }

    @Override
    public void setParent(Wizard parent) {
        this.parent = parent;
    }

    @Override
    public void hideWizardNavigation() {
        buttonsHBox.getChildren().remove(skipButton);
    }

    @FXML
    private void onSaved() {
        if (model.requestSavePassword())
            parent.nextStep(this);
        else
            log.debug(model.getErrorMessage()); // TODO use validating passwordTF
    }

    @FXML
    private void onSkipped() {
        parent.nextStep(this);
    }

    @FXML
    private void onOpenHelp() {
        Help.openWindow(HelpId.SETUP_PASSWORD);
    }
}

