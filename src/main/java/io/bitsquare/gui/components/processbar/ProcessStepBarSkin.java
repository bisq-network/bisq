/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */
package io.bitsquare.gui.components.processbar;

import io.bitsquare.gui.util.Colors;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.*;

import com.sun.javafx.scene.control.behavior.BehaviorBase;
import com.sun.javafx.scene.control.behavior.KeyBinding;
import com.sun.javafx.scene.control.skin.BehaviorSkinBase;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class ProcessStepBarSkin<T> extends BehaviorSkinBase<ProcessStepBar<T>, BehaviorBase<ProcessStepBar<T>>> {
    private static final Logger log = LoggerFactory.getLogger(ProcessStepBarSkin.class);

    private final ProcessStepBar<T> controller;
    private LabelWithBorder currentLabelWithBorder;
    private LabelWithBorder prevLabelWithBorder;
    private final List<LabelWithBorder> labelWithBorders = new ArrayList<>();

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProcessStepBarSkin(final ProcessStepBar<T> control) {
        super(control, new BehaviorBase<>(control, Collections.<KeyBinding>emptyList()));

        controller = getSkinnable();

        setProcessStepItems(controller.getProcessStepItems());
        setSelectedIndex(controller.getSelectedIndex());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void reset() {
        prevLabelWithBorder = null;
        for (LabelWithBorder labelWithBorder : labelWithBorders) {
            currentLabelWithBorder = labelWithBorder;
            currentLabelWithBorder.open();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Setters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void setProcessStepItems(List<ProcessStepItem> processStepItems) {
        if (processStepItems != null) {
            int i = 0;
            int size = controller.getProcessStepItems().size();
            for (ProcessStepItem processStepItem : controller.getProcessStepItems()) {

                LabelWithBorder labelWithBorder = new LabelWithBorder(processStepItem, i == 0, i == size - 1);
                getChildren().add(labelWithBorder);
                labelWithBorders.add(labelWithBorder);
                if (i == 0)
                    currentLabelWithBorder = prevLabelWithBorder = labelWithBorder;

                i++;
            }

            currentLabelWithBorder.current();
        }
    }

    public void setSelectedIndex(int index) {
        if (index < labelWithBorders.size()) {
            for (int i = 0; i <= index; i++) {

                if (prevLabelWithBorder != null)
                    prevLabelWithBorder.past();

                currentLabelWithBorder = labelWithBorders.get(i);
                currentLabelWithBorder.current();

                prevLabelWithBorder = currentLabelWithBorder;
            }
        }
    }

    @Override
    protected void layoutChildren(double x, double y, double width, double height) {
        double distance = 10;
        double padding = 50;
        for (int i = 0; i < getChildren().size(); i++) {
            Node node = getChildren().get(i);

            double newWidth = snapSize(node.prefWidth(height)) + padding;
            double newHeight = snapSize(node.prefHeight(-1) + 10);

            if (i > 0)
                x = x - ((LabelWithBorder) node).getArrowWidth();

            node.resize(newWidth, newHeight);
            // need to add 0.5 to make it sharp
            node.relocate(x + 0.5, 0.5);
            x += newWidth + distance;
        }
    }

    public static class LabelWithBorder extends Label {
        final double borderWidth = 1;
        private final double arrowWidth = 10;
        private final double arrowHeight = 30;

        private final ProcessStepItem processStepItem;
        private final boolean isFirst;
        private final boolean isLast;

        public LabelWithBorder(ProcessStepItem processStepItem, boolean isFirst, boolean isLast) {
            super(processStepItem.getLabel());

            this.processStepItem = processStepItem;

            this.isFirst = isFirst;
            this.isLast = isLast;

            setAlignment(Pos.CENTER);
            setStyle("-fx-font-size: 14");

            this.setShape(createButtonShape());

            open();
        }

        public void open() {
            log.debug("select " + processStepItem.getLabel());
            BorderStroke borderStroke = new BorderStroke(Colors.LIGHT_GREY, BorderStrokeStyle.SOLID, null,
                    new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
            setTextFill(Colors.LIGHT_GREY);
        }

        public void current() {
            log.debug("select " + processStepItem.getLabel());
            BorderStroke borderStroke = new BorderStroke(Colors.GREEN, BorderStrokeStyle.SOLID, null,
                    new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
            setTextFill(Colors.GREEN);
        }

        public void past() {
            log.debug("deSelect " + processStepItem.getLabel());
            BorderStroke borderStroke = new BorderStroke(Color.valueOf("#444444"), BorderStrokeStyle.SOLID, null,
                    new BorderWidths(borderWidth, borderWidth, borderWidth, borderWidth), Insets.EMPTY);
            this.setBorder(new Border(borderStroke));
            setTextFill(Color.valueOf("#444444"));
        }

        public double getArrowWidth() {
            return arrowWidth;
        }


        private Path createButtonShape() {
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

            if (!isLast) {
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

            if (!isFirst) {
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

        @Override
        public String toString() {
            return "LabelWithBorder{" +
                    ", processStepItem=" + processStepItem +
                    '}';
        }
    }
}
