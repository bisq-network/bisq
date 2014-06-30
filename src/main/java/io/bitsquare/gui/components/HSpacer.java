package io.bitsquare.gui.components;


import javafx.scene.layout.Pane;

@SuppressWarnings("WeakerAccess")
public class HSpacer extends Pane
{
    public HSpacer()
    {
    }

    public HSpacer(@SuppressWarnings("SameParameterValue") double width)
    {
        setPrefWidth(width);
    }

    @Override
    protected double computePrefWidth(double width)
    {
        return getPrefWidth();
    }
}

