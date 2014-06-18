package io.bitsquare.gui.components;


import javafx.scene.layout.Pane;

public class VSpacer extends Pane
{

    public VSpacer(double height)
    {
        setPrefHeight(height);
    }

    @Override
    protected double computePrefHeight(double width)
    {
        return getPrefHeight();
    }
}

