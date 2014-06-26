package io.bitsquare.gui.util;

import io.bitsquare.BitSquare;
import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialog;
import org.controlsfx.dialog.Dialogs;

import java.util.ArrayList;
import java.util.List;

public class Popups
{

    public static Action openErrorPopup(String title, String message)
    {
        return Dialogs.create()
                .title(title)
                .message(message)
                .nativeTitleBar()
                .lightweight()
                .showError();
    }

    public static Action openInsufficientMoneyPopup()
    {
        return openErrorPopup("Not enough money available", "There is not enough money available. Please pay in first to your wallet.");
    }

    public static Action openWarningPopup(String title, String message)
    {
        return Dialogs.create()
                .title(title)
                .message(message)
                .nativeTitleBar()
                .lightweight()
                .showWarning();
    }

    public static Action openRegistrationMissingPopup(String title, String message, String masthead, List<Dialogs.CommandLink> commandLinks, int selectedIndex)
    {
        return Dialogs.create()
                .title(title)
                .message(message)
                .masthead(masthead)
                .nativeTitleBar()
                .lightweight()
                .showCommandLinks(commandLinks.get(selectedIndex), commandLinks);
    }


    public static Action openConfirmPopup(String title, String masthead, String message)
    {
        List<Action> actions = new ArrayList<>();
        actions.add(Dialog.Actions.OK);
        actions.add(Dialog.Actions.CANCEL);
        return Dialogs.create()
                .owner(BitSquare.getStage())
                .title(title)
                .message(message)
                .masthead(masthead)
                .nativeTitleBar()
                .lightweight()
                .actions(actions)
                .showConfirm();
    }
}
