package io.bitsquare.gui.components.processbar;

import io.bitsquare.gui.util.Colors;
import javafx.scene.paint.Paint;

public class ProcessStepItem
{
    private String label;
    private Paint color;
    private boolean progressIndicator;

    public ProcessStepItem(String label)
    {
        this(label, Colors.BLUE, false);
    }

    public ProcessStepItem(String label, Paint color)
    {
        this(label, color, false);
    }

    public ProcessStepItem(String label, Paint color, boolean hasProgressIndicator)
    {
        this.label = label;
        this.color = color;
        this.progressIndicator = hasProgressIndicator;
    }

    public String getLabel()
    {
        return label;
    }

    public Paint getColor()
    {
        return color;
    }

    public boolean hasProgressIndicator()
    {
        return progressIndicator;
    }
}
