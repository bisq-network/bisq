package io.bitsquare.gui.util;

import org.controlsfx.dialog.Dialogs;

public class Popups
{
    public static void openErrorPopup(String title, String message)
    {
        Dialogs.create()
                .title(title)
                .message(message)
                .nativeTitleBar()
                .lightweight()
                .showError();
    }
}
