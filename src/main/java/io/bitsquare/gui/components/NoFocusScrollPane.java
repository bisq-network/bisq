package io.bitsquare.gui.components;

import javafx.scene.control.ScrollPane;

class NoFocusScrollPane extends ScrollPane
{
    public NoFocusScrollPane()
    {
    }

    public void requestFocus()
    {
        // prevent focus
    }
}
