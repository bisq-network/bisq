package io.bitsquare.gui.components.processbar;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.List;

public class ProcessStepBar<T> extends Control
{
    private List<ProcessStepItem> processStepItems;

    public ProcessStepBar(List<ProcessStepItem> processStepItems)
    {
        this.processStepItems = processStepItems;
    }

    @Override
    protected Skin<?> createDefaultSkin()
    {
        return new ProcessStepBarSkin<>(this);
    }

    List<ProcessStepItem> getProcessStepItems()
    {
        return processStepItems;
    }

    public void next()
    {
        ((ProcessStepBarSkin) getSkin()).next();
    }
}
