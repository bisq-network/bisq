package io.bitsquare.gui.components;

import javafx.scene.control.ScrollPane;

public class NoFocusScrollPane extends ScrollPane
{
    public void requestFocus()
    {
        // prevent focus
    }
}
