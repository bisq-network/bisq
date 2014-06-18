package io.bitsquare.gui.components;


import javafx.scene.layout.Pane;

public class HSpacer extends Pane
{

    public HSpacer(double width)
    {
        setPrefWidth(width);
    }

    @Override
    protected double computePrefWidth(double width)
    {
        return getPrefWidth();
    }
}

