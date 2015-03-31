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

package io.bitsquare.gui.util;

import io.bitsquare.gui.components.InfoDisplay;
import io.bitsquare.gui.components.InputTextField;
import io.bitsquare.gui.components.TextFieldWithCopyIcon;
import io.bitsquare.gui.components.TitledGroupBg;
import io.bitsquare.gui.components.TxIdTextField;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ComponentBuilder {
    private static final Logger log = LoggerFactory.getLogger(ComponentBuilder.class);

    public static GridPane getAndAddGridPane(Pane parent) {
        GridPane gridPane = new GridPane();
        AnchorPane.setLeftAnchor(gridPane, 10d);
        AnchorPane.setRightAnchor(gridPane, 10d);
        AnchorPane.setTopAnchor(gridPane, 10d);
        AnchorPane.setBottomAnchor(gridPane, 10d);
        gridPane.setHgap(Layout.GRID_GAP);
        gridPane.setVgap(Layout.GRID_GAP);
        ColumnConstraints columnConstraints1 = new ColumnConstraints();
        columnConstraints1.setHalignment(HPos.RIGHT);
        columnConstraints1.setHgrow(Priority.SOMETIMES);

        ColumnConstraints columnConstraints2 = new ColumnConstraints();
        columnConstraints2.setHgrow(Priority.ALWAYS);

        gridPane.getColumnConstraints().addAll(columnConstraints1, columnConstraints2);

        parent.getChildren().add(gridPane);
        return gridPane;
    }

    public static TitledGroupBg getAndAddTitledGroupBg(GridPane gridPane, int rowIndex, int rowSpan, String title) {
        return getAndAddTitledGroupBg(gridPane, rowIndex, rowSpan, title, 0);
    }

    public static TitledGroupBg getAndAddTitledGroupBg(GridPane gridPane, int rowIndex, int rowSpan, String title, double top) {
        TitledGroupBg titledGroupBg = new TitledGroupBg();
        titledGroupBg.setText(title);
        titledGroupBg.prefWidthProperty().bind(gridPane.widthProperty());
        GridPane.setRowIndex(titledGroupBg, rowIndex);
        GridPane.setRowSpan(titledGroupBg, rowSpan);
        GridPane.setColumnSpan(titledGroupBg, 2);
        GridPane.setMargin(titledGroupBg, new Insets(top, -10, -10, -10));
        gridPane.getChildren().add(titledGroupBg);
        return titledGroupBg;
    }

    private static Label getAndAddLabel(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = new Label(title);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }

    public static Label getAndAddInfoLabel(GridPane gridPane, int rowIndex, double top) {
        Label label = new Label();
        label.setWrapText(true);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setColumnSpan(label, 2);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }

    public static LabelTextFieldPair getAndAddLabelTextFieldPair(GridPane gridPane, int rowIndex, String title) {
        return getAndAddLabelTextFieldPair(gridPane, rowIndex, title, 0);
    }

    public static LabelTextFieldPair getAndAddLabelTextFieldPair(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = getAndAddLabel(gridPane, rowIndex, title, top);

        TextField textField = new TextField();
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        GridPane.setRowIndex(textField, rowIndex);
        GridPane.setColumnIndex(textField, 1);
        GridPane.setMargin(textField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textField);

        return new LabelTextFieldPair(label, textField);
    }

    public static LabelInputTextFieldPair getAndAddLabelInputTextFieldPair(GridPane gridPane, int rowIndex, String title) {
        return getAndAddLabelInputTextFieldPair(gridPane, rowIndex, title, 0);
    }

    public static LabelInputTextFieldPair getAndAddLabelInputTextFieldPair(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = getAndAddLabel(gridPane, rowIndex, title, top);

        InputTextField inputTextField = new InputTextField();
        GridPane.setRowIndex(inputTextField, rowIndex);
        GridPane.setColumnIndex(inputTextField, 1);
        GridPane.setMargin(inputTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(inputTextField);

        return new LabelInputTextFieldPair(label, inputTextField);
    }

    public static LabelTxIdTextFieldPair getAndAddLabelTxIdTextFieldPair(GridPane gridPane, int rowIndex, String title) {
        return getAndAddLabelTxIdTextFieldPair(gridPane, rowIndex, title, 0);
    }

    public static LabelTxIdTextFieldPair getAndAddLabelTxIdTextFieldPair(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = getAndAddLabel(gridPane, rowIndex, title, top);

        TxIdTextField txIdTextField = new TxIdTextField();
        GridPane.setRowIndex(txIdTextField, rowIndex);
        GridPane.setColumnIndex(txIdTextField, 1);
        GridPane.setMargin(txIdTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(txIdTextField);

        return new LabelTxIdTextFieldPair(label, txIdTextField);
    }

    public static LabelTextFieldWithCopyIconPair getAndAddLabelTextFieldWithCopyIconPair(GridPane gridPane, int rowIndex, String title) {
        return getAndAddLabelTextFieldWithCopyIconPair(gridPane, rowIndex, title, 0);
    }

    public static LabelTextFieldWithCopyIconPair getAndAddLabelTextFieldWithCopyIconPair(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = getAndAddLabel(gridPane, rowIndex, title, top);

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon();
        GridPane.setRowIndex(textFieldWithCopyIcon, rowIndex);
        GridPane.setColumnIndex(textFieldWithCopyIcon, 1);
        GridPane.setMargin(textFieldWithCopyIcon, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textFieldWithCopyIcon);

        return new LabelTextFieldWithCopyIconPair(label, textFieldWithCopyIcon);
    }

    public static InfoDisplay getAndAddInfoDisplay(GridPane gridPane,
                                                   int rowIndex,
                                                   String text,
                                                   EventHandler<ActionEvent> onActionHandler) {
        return getAndAddInfoDisplay(gridPane, rowIndex, text, onActionHandler, 0);
    }

    public static InfoDisplay getAndAddInfoDisplay(GridPane gridPane,
                                                   int rowIndex,
                                                   String text,
                                                   EventHandler<ActionEvent> onActionHandler,
                                                   double top) {
        InfoDisplay infoDisplay = new InfoDisplay();
        infoDisplay.setText(text);
        infoDisplay.setOnAction(onActionHandler);
        GridPane.setRowIndex(infoDisplay, rowIndex);
        GridPane.setMargin(infoDisplay, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(infoDisplay);

        return infoDisplay;
    }

    public static Button getAndAddButton(GridPane gridPane,
                                         int rowIndex,
                                         String title,
                                         EventHandler<ActionEvent> onActionHandler) {
        Button button = new Button(title);
        button.setDefaultButton(true);
        button.setOnAction(onActionHandler);
        GridPane.setRowIndex(button, rowIndex);
        GridPane.setColumnIndex(button, 1);
        GridPane.setMargin(button, new Insets(15, 0, 40, 0));
        gridPane.getChildren().add(button);
        return button;
    }

    public static class LabelTextFieldPair {
        public Label label;
        public TextField textField;

        public LabelTextFieldPair(Label label, TextField textField) {
            this.label = label;
            this.textField = textField;
        }
    }

    public static class LabelInputTextFieldPair {
        public Label label;
        public InputTextField inputTextField;

        public LabelInputTextFieldPair(Label label, InputTextField inputTextField) {
            this.label = label;
            this.inputTextField = inputTextField;
        }
    }

    public static class LabelTxIdTextFieldPair {
        public Label label;
        public TxIdTextField txIdTextField;

        public LabelTxIdTextFieldPair(Label label, TxIdTextField txIdTextField) {
            this.label = label;
            this.txIdTextField = txIdTextField;
        }
    }

    public static class LabelTextFieldWithCopyIconPair {
        public Label label;
        public TextFieldWithCopyIcon textFieldWithCopyIcon;

        public LabelTextFieldWithCopyIconPair(Label label, TextFieldWithCopyIcon textFieldWithCopyIcon) {
            this.label = label;
            this.textFieldWithCopyIcon = textFieldWithCopyIcon;
        }
    }

}
