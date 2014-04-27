package io.bitsquare.gui.components.processbar;

import javafx.scene.control.Control;
import javafx.scene.control.Skin;

import java.util.List;

public class ProcessStepBar<T> extends Control
{


    private List<ProcessStepItem> processStepItems = null;

    public ProcessStepBar()
    {
    }


    public ProcessStepBar(List<ProcessStepItem> processStepItems)
    {
        this.processStepItems = processStepItems;
    }

    @Override
    protected Skin<?> createDefaultSkin()
    {
        return new ProcessStepBarSkin<>(this);
    }

    public void setProcessStepItems(List<ProcessStepItem> processStepItems)
    {
        this.processStepItems = processStepItems;
        if (getSkin() != null)
            ((ProcessStepBarSkin) getSkin()).dataChanged();
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
