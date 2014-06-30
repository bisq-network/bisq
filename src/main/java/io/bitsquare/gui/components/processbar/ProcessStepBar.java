package io.bitsquare.gui.components.processbar;

import java.util.List;
import javafx.scene.control.Control;
import javafx.scene.control.Skin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProcessStepBar<T> extends Control
{


    @Nullable
    private List<ProcessStepItem> processStepItems = null;

    public ProcessStepBar()
    {
    }


    public ProcessStepBar(@Nullable List<ProcessStepItem> processStepItems)
    {
        this.processStepItems = processStepItems;
    }

    @NotNull
    @Override
    protected Skin<?> createDefaultSkin()
    {
        return new ProcessStepBarSkin<>(this);
    }

    @Nullable
    List<ProcessStepItem> getProcessStepItems()
    {
        return processStepItems;
    }

    public void setProcessStepItems(@Nullable List<ProcessStepItem> processStepItems)
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
