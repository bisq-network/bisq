package io.bitsquare.gui.components.processbar;

import java.util.List;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;

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


    List<ProcessStepItem> getProcessStepItems()
    {
        return processStepItems;
    }

    public void setProcessStepItems(List<ProcessStepItem> processStepItems)
    {
        this.processStepItems = processStepItems;
        if (getSkin() != null)
            ((ProcessStepBarSkin) getSkin()).dataChanged();
    }

    public void next()
    {
        ((ProcessStepBarSkin) getSkin()).next();
    }
}
