package io.bitsquare.gui.components;

import com.google.bitcoin.store.BlockStoreException;
import com.google.common.base.Throwables;
import io.bitsquare.BitSquare;
import java.util.ArrayList;
import java.util.List;
import javafx.application.Platform;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

@SuppressWarnings({"SameParameterValue", "WeakerAccess"})
public class Popups
{

    // Information
    public static void openInformationPopup(String title, String message)
    {
        openInformationPopup(title, message, null);
    }

    public static void openInformationPopup(String title, String message, String masthead)
    {
        Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).showInformation();
    }

    // Confirm
    public static Action openConfirmPopup(String title, String message)
    {
        return openConfirmPopup(title, message, null);
    }

    public static Action openConfirmPopup(String title, String message, String masthead)
    {
        List<Action> actions = new ArrayList<>();
        actions.add(Dialog.Actions.OK);
        actions.add(Dialog.Actions.CANCEL);
        return openConfirmPopup(title, message, masthead, actions);
    }

    public static Action openConfirmPopup(String title, String message, String masthead, List<Action> actions)
    {
        return Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).actions(actions).showConfirm();
    }

    // Warning
    public static void openWarningPopup(String message)
    {
        openWarningPopup("Warning", message, null);
    }

    public static void openWarningPopup(String title, String message)
    {
        openWarningPopup(title, message, null);
    }

    public static void openWarningPopup(String title, String message, String masthead)
    {
        Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).showWarning();
    }

    // Error
    public static Action openErrorPopup(String message)
    {
        return openErrorPopup("Error", message);
    }

    public static Action openErrorPopup(String title, String message)
    {
        return openErrorPopup(title, message, null);
    }

    public static Action openErrorPopup(String title, String message, String masthead)
    {
        return Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).showError();
    }

    // Exception
    public static Action openExceptionPopup(Throwable throwable)
    {
        return openExceptionPopup(throwable, "Exception", "That should not have happened...");
    }

    public static Action openExceptionPopup(Throwable throwable, String title, String message)
    {
        return openExceptionPopup(throwable, title, message, null);
    }

    public static Action openExceptionPopup(Throwable throwable, String title, String message, String masthead)
    {
        return Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).showException(throwable);
    }

    // Support handling of uncaught exception from any thread (also non gui thread)
    @SuppressWarnings("ThrowableResultOfMethodCallIgnored")
    public static void handleUncaughtExceptions(Throwable throwable)
    {
        // while dev
        throwable.printStackTrace();

        Runnable runnable = () -> {
            if (Throwables.getRootCause(throwable) instanceof BlockStoreException)
            {
                Action response = Popups.openErrorPopup("Application already running", "This application is already running and cannot be started twice.", "");
                if (response == Dialog.Actions.OK) Platform.exit();
            }
            else
            {
                Action response = Popups.openExceptionPopup(throwable, "Exception", "", "A critical error has occurred.\nPlease copy the exception details and send a bug report to bugs@bitsquare.io.");
                if (response == Dialog.Actions.OK) Platform.exit();
            }
        };

        if (Platform.isFxApplicationThread()) runnable.run();
        else Platform.runLater(runnable);
    }

    // custom
    public static void openInsufficientMoneyPopup()
    {
        openWarningPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.", null);
    }

    public static Action openRegistrationMissingPopup(String title, String message, String masthead, List<Dialogs.CommandLink> commandLinks, int selectedIndex)
    {
        return Dialogs.create().owner(BitSquare.getPrimaryStage()).title(title).message(message).masthead(masthead).showCommandLinks(commandLinks.get(selectedIndex), commandLinks);
    }
}
