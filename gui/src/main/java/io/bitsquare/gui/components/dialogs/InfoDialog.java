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

package io.bitsquare.gui.components.dialogs;

import io.bitsquare.locale.BSResources;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import org.controlsfx.control.ButtonBar;
import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.DialogStyle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO under construction
public class InfoDialog {
    private static final Logger log = LoggerFactory.getLogger(InfoDialog.class);

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public InfoDialog(Stage stage) {

        final TextField username = new TextField();
        final PasswordField password = new PasswordField();
        final Action actionLogin = new AbstractAction("Login") {
            // This method is called when the login button is clicked ...
            public void handle(ActionEvent ae) {
                Dialog d = (Dialog) ae.getSource();
                // Do the login here.
                d.hide();
            }
        };

        // Create the custom dialog.
        Dialog dlg = new Dialog(stage, "Login Dialog");
        dlg.setIconifiable(false);
        dlg.setClosable(false);
        dlg.setResizable(false);
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(0, 10, 0, 10));

        username.setPromptText("Username");
        password.setPromptText("Password");

        grid.add(new Label("Username:"), 0, 0);
        grid.add(username, 1, 0);
        grid.add(new Label("Password:"), 0, 1);
        grid.add(password, 1, 1);

        ButtonBar.setType(actionLogin, ButtonBar.ButtonType.OK_DONE);
        actionLogin.disabledProperty().set(true);

        // Do some validation (using the Java 8 lambda syntax).
        username.textProperty().addListener((observable, oldValue, newValue) -> actionLogin.disabledProperty().set(newValue.trim().isEmpty()));

        dlg.setMasthead("Look, a Custom Login Dialog");
        dlg.setContent(grid);
        dlg.getActions().addAll(actionLogin, Dialog.Actions.CANCEL);

        // Request focus on the username field by default.
        Platform.runLater(username::requestFocus);

        dlg.show();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Public methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void show(Object owner, String title, String masthead, String message) {

        Dialog dlg = new Dialog(owner, title, false, DialogStyle.CROSS_PLATFORM_DARK);
        dlg.setResizable(false);
        dlg.setIconifiable(false);
        dlg.setClosable(false);

        Image image = new Image(InfoDialog.class.getResource
                ("/impl/org/controlsfx/dialog/resources/oxygen/48/dialog-information.png").toString());
        if (image != null) {
            dlg.setGraphic(new ImageView(image));
        }
        dlg.setMasthead(masthead);
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CLOSE");
                Dialog.Actions.CLOSE.handle(actionEvent);
                // overlayManager.removeBlurContent();
            }
        });
        dlg.getActions().addAll(actions);
        // dlg.setBackgroundEffect(backgroundEffect);
    }

}
