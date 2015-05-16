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

import io.bitsquare.gui.OverlayManager;
import io.bitsquare.locale.BSResources;

import java.util.ArrayList;
import java.util.List;

import javafx.event.ActionEvent;
import javafx.stage.Stage;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Popups {
    private static final Logger log = LoggerFactory.getLogger(Popups.class);

    public static Stage primaryStage;

    public static void removeBlurContent() {
        OverlayManager.removeBlurContent();
    }

    // Information
    public static void openInfoPopup(String message) {
        openInfoPopup(null, message);
    }

    public static void openInfoPopup(String masthead, String message) {
        OverlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CLOSE");
                Dialog.Actions.CLOSE.handle(actionEvent);
            }
        });
        openInfoPopup(masthead, message, actions);
    }

    public static void openInfoPopup(String masthead, String message, List<Action> actions) {
        Dialogs.create()
                .owner(primaryStage)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showInformation();
        removeBlurContent();
    }

    // Confirm
    public static Action openConfirmPopup(String title, String message) {
        return openConfirmPopup(title, null, message);
    }

    public static Action openConfirmPopup(String title, String masthead, String message) {
        OverlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.ok")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "OK");
                Dialog.Actions.OK.handle(actionEvent);
            }
        });
        actions.add(new AbstractAction(BSResources.get("shared.cancel")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CANCEL");
                Dialog.Actions.CANCEL.handle(actionEvent);
            }
        });
        return openConfirmPopup(title, masthead, message, actions);
    }

    public static Action openConfirmPopup(String title, String masthead, String message, List<Action> actions) {
        return Dialogs.create()
                .owner(primaryStage)
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showConfirm();
    }

    // Warning
    public static void openWarningPopup(String message) {
        openWarningPopup("Warning", message);
    }

    public static void openWarningPopup(String title, String message) {
        openWarningPopup(title, null, message);
    }

    public static void openWarningPopup(String title, String masthead, String message) {
        OverlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CLOSE");
                Dialog.Actions.CLOSE.handle(actionEvent);
            }
        });
        openWarningPopup(title, masthead, message, actions);
    }

    private static void openWarningPopup(String title, String masthead, String message, List<Action> actions) {
        Dialogs.create()
                .owner(primaryStage)
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showWarning();
        removeBlurContent();
    }

    // Error
    public static void openErrorPopup(String message) {
        openErrorPopup("Error", message);
    }

    public static void openErrorPopup(String title, String message) {
        openErrorPopup(title, null, message);
    }

    public static void openErrorPopup(String title, String masthead, String message) {
        OverlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CLOSE");
                Dialog.Actions.CLOSE.handle(actionEvent);
            }
        });
        openErrorPopup(title, masthead, message, actions);
    }

    private static void openErrorPopup(String title, String masthead, String message, List<Action> actions) {
        Dialogs.create()
                .owner(primaryStage)
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showError();
        removeBlurContent();
    }

    // Exception
    public static void openExceptionPopup(Throwable throwable) {
        openExceptionPopup(throwable, "Exception", "That should not have happened...");
    }

    public static void openExceptionPopup(Throwable throwable, String title, String message) {
        openExceptionPopup(throwable, title, null, message);
    }

    private static void openExceptionPopup(Throwable throwable, String title, String masthead, String message) {
        OverlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                getProperties().put("type", "CLOSE");
                Dialog.Actions.CLOSE.handle(actionEvent);
            }
        });
        Dialogs.create()
                .owner(primaryStage)
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showException(throwable);
        removeBlurContent();
    }


    // custom
    public static void openInsufficientMoneyPopup() {
        openWarningPopup("Warning", "There is not enough money available",
                "Please pay in first to your wallet.");
    }

    public static boolean isOK(Action response) {
        return response.getProperties().get("type").equals("OK");
    }

    public static boolean isYes(Action response) {
        return response.getProperties().get("type").equals("YES");
    }

}
