package io.bitsquare.gui.components.processbar;

import io.bitsquare.util.Utils;
import javafx.animation.AnimationTimer;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.List;

public class ProcessStepsBuilder
{
    protected int index = 0;
    private Control previousControl;
    private Pane controlHolder;
    protected Object controller;
    protected List<ProcessStepItem> processStepItems = new ArrayList();
    protected ProcessStepBar<String> processStepBar;

    public void build(Pane processStepBarHolder, Pane controlHolder, Object controller)
    {
        this.controlHolder = controlHolder;
        this.controller = controller;

        fillProcessStepItems();

        processStepBar = new ProcessStepBar(processStepItems);
        processStepBar.relocate(10, 10);

        processStepBarHolder.getChildren().add(processStepBar);

        update();
    }

    public void next()
    {
        index++;
        update();
        processStepBar.next();
    }

    // template
    protected void fillProcessStepItems()
    {
        // to be defined in subclasses
    }

    protected void update()
    {
        if (index < processStepItems.size())
        {
            ProcessStepItem processStepItem = processStepItems.get(index);
            if (previousControl != null)
                controlHolder.getChildren().remove(previousControl);

            if (processStepItem.hasProgressIndicator())
            {
                final ProgressIndicator progressIndicator = new ProgressIndicator();
                progressIndicator.setProgress(-1.0);
                progressIndicator.setPrefSize(30.0, 30.0);
                controlHolder.getChildren().add(progressIndicator);
                previousControl = progressIndicator;

                // TODO
                // mock simulate network delay
                Utils.setTimeout(100, (AnimationTimer animationTimer) -> {
                    next();
                    return null;
                });
            }
            else
            {
                final Button button = new Button(processStepItem.getLabel());
                button.setOnAction(e -> next());

                controlHolder.getChildren().add(button);
                previousControl = button;
            }
        }
    }
}

