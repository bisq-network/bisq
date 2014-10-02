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

import io.bitsquare.BitSquare;
import io.bitsquare.gui.OverlayManager;
import io.bitsquare.locale.BSResources;

import com.google.bitcoin.store.BlockStoreException;

import com.google.common.base.Throwables;

import java.util.ArrayList;
import java.util.List;

import javafx.application.Platform;
import javafx.event.ActionEvent;

import org.controlsfx.control.action.AbstractAction;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Popups {
    private static final Logger log = LoggerFactory.getLogger(Popups.class);

    // TODO just temporary, class will be removed completely
    public static void setOverlayManager(OverlayManager overlayManager) {
        Popups.overlayManager = overlayManager;
    }

    private static OverlayManager overlayManager;

    // Information
    public static void openInfo(String message) {
        openInfo(null, message);
    }

    public static void openInfo(String masthead, String message) {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.CLOSE.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        openInfo(masthead, message, actions);
    }

    public static void openInfo(String masthead, String message, List<Action> actions) {
        Dialogs.create()
                .owner(BitSquare.getPrimaryStage())
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showInformation();
    }

    // Confirm
    public static Action openConfirmPopup(String title, String message) {
        return openConfirmPopup(title, null, message);
    }

    public static Action openConfirmPopup(String title, String masthead, String message) {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.ok")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.OK.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        actions.add(new AbstractAction(BSResources.get("shared.cancel")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.CANCEL.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        return openConfirmPopup(title, masthead, message, actions);
    }

    public static Action openConfirmPopup(String title, String masthead, String message, List<Action> actions) {
        return Dialogs.create()
                .owner(BitSquare.getPrimaryStage())
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
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.CLOSE.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        openWarningPopup(title, masthead, message, actions);
    }

    private static void openWarningPopup(String title, String masthead, String message, List<Action> actions) {
        Dialogs.create()
                .owner(BitSquare.getPrimaryStage())
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showWarning();
    }

    // Error
    public static Action openErrorPopup(String message) {
        return openErrorPopup("Error", message);
    }

    public static Action openErrorPopup(String title, String message) {
        return openErrorPopup(title, null, message);
    }

    public static Action openErrorPopup(String title, String masthead, String message) {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.CLOSE.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        return openErrorPopup(title, masthead, message, actions);
    }

    private static Action openErrorPopup(String title, String masthead, String message, List<Action> actions) {
        return Dialogs.create()
                .owner(BitSquare.getPrimaryStage())
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showError();
    }

    // Exception
    public static Action openExceptionPopup(Throwable throwable) {
        return openExceptionPopup(throwable, "Exception", "That should not have happened...");
    }

    public static Action openExceptionPopup(Throwable throwable, String title, String message) {
        return openExceptionPopup(throwable, title, null, message);
    }

    private static Action openExceptionPopup(Throwable throwable, String title, String masthead, String message) {
        overlayManager.blurContent();
        List<Action> actions = new ArrayList<>();
        actions.add(new AbstractAction(BSResources.get("shared.close")) {
            @Override
            public void handle(ActionEvent actionEvent) {
                Dialog.Actions.CLOSE.handle(actionEvent);
                overlayManager.removeBlurContent();
            }
        });
        return Dialogs.create()
                .owner(BitSquare.getPrimaryStage())
                .title(title)
                .message(message)
                .masthead(masthead)
                .actions(actions)
                .showException(throwable);
    }

    // Support handling of uncaught exception from any thread (also non gui thread)
    public static void handleUncaughtExceptions(Throwable throwable) {
        // while dev
        log.error(throwable.getMessage());
        log.error(throwable.toString());
        throwable.printStackTrace();

        Runnable runnable = () -> {
            if (Throwables.getRootCause(throwable) instanceof BlockStoreException) {
                Action response = Popups.openErrorPopup("Error", "Application already running",
                        "This application is already running and cannot be started twice.\n\n " +
                                "Check your system tray to reopen the window of the running application.");
                if (response == Dialog.Actions.OK)
                    Platform.exit();
            }
            else {
                Action response = Popups.openExceptionPopup(throwable, "Exception", "A critical error has occurred.",
                        "Please copy the exception details and open a bug report at:\n " +
                                "https://github.com/bitsquare/bitsquare/issues.");
                if (response == Dialog.Actions.OK)
                    Platform.exit();
            }
        };

        if (Platform.isFxApplicationThread())
            runnable.run();
        else
            Platform.runLater(runnable);
    }

    // custom
    public static void openInsufficientMoneyPopup() {
        openWarningPopup("Warning", "There is not enough money available",
                "Please pay in first to your wallet.");
    }
}
