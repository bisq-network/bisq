package io.bitsquare.gui.components.processbar;

import com.sun.javafx.scene.control.behavior.BehaviorBase;
import com.sun.javafx.scene.control.behavior.KeyBinding;
import com.sun.javafx.scene.control.skin.BehaviorSkinBase;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;

class ProcessStepBarSkin<T> extends BehaviorSkinBase<ProcessStepBar<T>, BehaviorBase<ProcessStepBar<T>>>
{
    private final ProcessStepBar<T> controller;
    private LabelWithBorder currentLabelWithBorder;
    private LabelWithBorder prevLabelWithBorder;
    private int index;
    private List<LabelWithBorder> labelWithBorders;

    public ProcessStepBarSkin(final ProcessStepBar<T> control)
    {
        super(control, new BehaviorBase<>(control, Collections.<KeyBinding>emptyList()));

        controller = getSkinnable();

        applyData();
    }

    public void dataChanged()
    {
        applyData();
    }

    private void applyData()
    {
        if (controller.getProcessStepItems() != null)
        {
            int i = 0;
            labelWithBorders = new ArrayList<>();
            int size = controller.getProcessStepItems().size();
            for (ProcessStepItem processStepItem : controller.getProcessStepItems())
            {
                LabelWithBorder labelWithBorder = new LabelWithBorder(processStepItem, i == 0, i == size - 1);
                getChildren().add(labelWithBorder);
                labelWithBorders.add(labelWithBorder);
                if (i == 0)
                {
                    currentLabelWithBorder = prevLabelWithBorder = labelWithBorder;
                }

                i++;
            }

            currentLabelWithBorder.select();
        }
    }

    public void next()
    {
        index++;

        prevLabelWithBorder.deSelect();
        if (index < labelWithBorders.size())
        {
            currentLabelWithBorder = labelWithBorders.get(index);
            currentLabelWithBorder.select();

            prevLabelWithBorder = currentLabelWithBorder;
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double width, double height)
    {
        double distance = 10;
        double padding = 50;
        for (int i = 0; i < getChildren().size(); i++)
        {
            Node node = getChildren().get(i);

            double newWidth = snapSize(node.prefWidth(height)) + padding;
            double newHeight = snapSize(node.prefHeight(-1) + 10);

            if (i > 0)
            {
                x = snapPosition(x - ((LabelWithBorder) node).getArrowWidth());
            }

            x = snapPosition(x);
            y = snapPosition(y);
            node.resize(newWidth, newHeight);
            node.relocate(x, y);
            x += newWidth + distance;
        }
    }


    @SuppressWarnings("EmptyMethod")
    public static class LabelWithBorder extends Label
    {
        final double borderWidth = 1;
        private final double arrowWidth = 10;
        private final double arrowHeight = 30;

        private final ProcessStepItem processStepItem;
        private final boolean isFirst;
        private final boolean isLast;

        public LabelWithBorder(ProcessStepItem processStepItem, boolean isFirst, boolean isLast)
        {
            super(processStepItem.getLabel());
            this.processStepItem = processStepItem;

            this.isFirst = isFirst;
            this.isLast = isLast;

            setAlignment(Pos.CENTER);
            setTextFill(Color.GRAY);
            setStyle("-fx-font-size: 14");

            this.setShape(createButtonShape());

            BorderStroke borderStroke = new BorderStroke(Color.LIGHTGRAY, BorderStrokeStyle.SOLID, null, new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
        }

        public void select()
        {
            BorderStroke borderStroke = new BorderStroke(processStepItem.getColor(), BorderStrokeStyle.SOLID, null, new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
            setTextFill(processStepItem.getColor());
        }

        public void deSelect()
        {
            /*BorderStroke borderStroke = new BorderStroke(Color.GRAY, BorderStrokeStyle.SOLID, null,
                    new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
            setTextFill(Color.GRAY);  */
        }


        public double getArrowWidth()
        {
            return arrowWidth;
        }


        private Path createButtonShape()
        {
            // build the following shape (or home without left arrow)

            //   --------
            //  \         \
            //  /         /
            //   --------
            Path path = new Path();

            // begin in the upper left corner
            MoveTo e1 = new MoveTo(0, 0);
            path.getElements().add(e1);

            // draw a horizontal line that defines the width of the shape
            HLineTo e2 = new HLineTo();
            // bind the width of the shape to the width of the button
            e2.xProperty().bind(this.widthProperty().subtract(arrowWidth));
            path.getElements().add(e2);

            if (!isLast)
            {
                // draw upper part of right arrow
                LineTo e3 = new LineTo();
                // the x endpoint of this line depends on the x property of line e2
                e3.xProperty().bind(e2.xProperty().add(arrowWidth));
                e3.setY(arrowHeight / 2.0);
                path.getElements().add(e3);
            }


            // draw lower part of right arrow
            LineTo e4 = new LineTo();
            // the x endpoint of this line depends on the x property of line e2
            e4.xProperty().bind(e2.xProperty());
            e4.setY(arrowHeight);
            path.getElements().add(e4);

            // draw lower horizontal line
            HLineTo e5 = new HLineTo(0);
            path.getElements().add(e5);

            if (!isFirst)
            {
                LineTo e6 = new LineTo(arrowWidth, arrowHeight / 2.0);
                path.getElements().add(e6);
            }

            // close path
            ClosePath e7 = new ClosePath();
            path.getElements().add(e7);
            // this is a dummy color to fill the shape, it won't be visible
            path.setFill(Color.BLACK);

            return path;
        }


    }
}
