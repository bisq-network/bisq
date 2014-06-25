package io.bitsquare.gui.util;

import org.controlsfx.control.action.Action;
import org.controlsfx.dialog.Dialogs;

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

    public static void openWarningPopup(String title, String message)
    {
        Dialogs.create()
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


}
