/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.gui.util;

import de.jensd.fx.fontawesome.AwesomeIcon;
import io.bisq.common.locale.Res;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Tuple3;
import io.bisq.common.util.Tuple4;
import io.bisq.gui.components.*;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class FormBuilder {
    private static final Logger log = LoggerFactory.getLogger(FormBuilder.class);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // GridPane
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static GridPane addGridPane(Pane parent) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // TitledGroupBg
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane, int rowIndex, int rowSpan, String title) {
        return addTitledGroupBg(gridPane, rowIndex, rowSpan, title, 0);
    }

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane, int rowIndex, int rowSpan, String title, double top) {
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Label addLabel(GridPane gridPane, int rowIndex, String title) {
        return addLabel(gridPane, rowIndex, title, 0);
    }

    public static Label addLabel(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = new Label(title);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Multiline Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Label addMultilineLabel(GridPane gridPane, int rowIndex) {
        return addMultilineLabel(gridPane, rowIndex, 0);
    }

    public static Label addMultilineLabel(GridPane gridPane, int rowIndex, String text) {
        return addMultilineLabel(gridPane, rowIndex, text, 0);
    }

    public static Label addMultilineLabel(GridPane gridPane, int rowIndex, double top) {
        return addMultilineLabel(gridPane, rowIndex, "", top);
    }

    public static Label addMultilineLabel(GridPane gridPane, int rowIndex, String text, double top) {
        Label label = new Label(text);
        label.setWrapText(true);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setColumnSpan(label, 2);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextField> addLabelTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelTextField(gridPane, rowIndex, title, "", 0);
    }

    public static Tuple2<Label, TextField> addLabelTextField(GridPane gridPane, int rowIndex, String title, String value) {
        return addLabelTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TextField> addLabelTextField(GridPane gridPane, int rowIndex, String title, double top) {
        return addLabelTextField(gridPane, rowIndex, title, "", top);
    }

    public static Tuple2<Label, TextField> addLabelTextField(GridPane gridPane, int rowIndex, String title, String value, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TextField textField = new TextField(value);
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        GridPane.setRowIndex(textField, rowIndex);
        GridPane.setColumnIndex(textField, 1);
        GridPane.setMargin(textField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textField);

        return new Tuple2<>(label, textField);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    //  HyperlinkWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static HyperlinkWithIcon addHyperlinkWithIcon(GridPane gridPane, int rowIndex, String title, String url) {
        return addHyperlinkWithIcon(gridPane, rowIndex, title, url, 0);
    }

    public static HyperlinkWithIcon addHyperlinkWithIcon(GridPane gridPane, int rowIndex, String title, String url, double top) {
        HyperlinkWithIcon hyperlinkWithIcon = new HyperlinkWithIcon(title, AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(url));
        GridPane.setRowIndex(hyperlinkWithIcon, rowIndex);
        GridPane.setColumnIndex(hyperlinkWithIcon, 0);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(top, 0, 0, -4));
        gridPane.getChildren().add(hyperlinkWithIcon);
        return hyperlinkWithIcon;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + HyperlinkWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, HyperlinkWithIcon> addLabelHyperlinkWithIcon(GridPane gridPane, int rowIndex,
                                                                             String labelTitle, String title,
                                                                             String url) {
        return addLabelHyperlinkWithIcon(gridPane, rowIndex, labelTitle, title, url, 0);
    }

    public static Tuple2<Label, HyperlinkWithIcon> addLabelHyperlinkWithIcon(GridPane gridPane, int rowIndex, String labelTitle, String title, String url, double top) {
        Label label = addLabel(gridPane, rowIndex, labelTitle, top);

        HyperlinkWithIcon hyperlinkWithIcon = new HyperlinkWithIcon(title, AwesomeIcon.EXTERNAL_LINK);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(url));
        GridPane.setRowIndex(hyperlinkWithIcon, rowIndex);
        GridPane.setColumnIndex(hyperlinkWithIcon, 1);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(top, 0, 0, -4));
        gridPane.getChildren().add(hyperlinkWithIcon);
        return new Tuple2<>(label, hyperlinkWithIcon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextArea
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextArea> addLabelTextArea(GridPane gridPane, int rowIndex, String title, String prompt) {
        return addLabelTextArea(gridPane, rowIndex, title, prompt, 0);
    }

    public static Tuple2<Label, TextArea> addLabelTextArea(GridPane gridPane, int rowIndex, String title, String prompt, double top) {
        Label label = addLabel(gridPane, rowIndex, title, 0);
        label.setAlignment(Pos.TOP_RIGHT);
        GridPane.setMargin(label, new Insets(top + 4, 0, 0, 0));
        GridPane.setValignment(label, VPos.TOP);

        TextArea textArea = new TextArea();
        textArea.setPromptText(prompt);
        textArea.setWrapText(true);
        GridPane.setRowIndex(textArea, rowIndex);
        GridPane.setColumnIndex(textArea, 1);
        GridPane.setMargin(textArea, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textArea);

        return new Tuple2<>(label, textArea);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + DatePicker
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, DatePicker> addLabelDatePicker(GridPane gridPane, int rowIndex, String title) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        DatePicker datePicker = new DatePicker();
        GridPane.setRowIndex(datePicker, rowIndex);
        GridPane.setColumnIndex(datePicker, 1);
        gridPane.getChildren().add(datePicker);

        return new Tuple2<>(label, datePicker);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TxIdTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("UnusedReturnValue")
    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane, int rowIndex, String title, String value) {
        return addLabelTxIdTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane, int rowIndex, String title, String value, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TxIdTextField txTextField = new TxIdTextField();
        txTextField.setup(value);
        GridPane.setRowIndex(txTextField, rowIndex);
        GridPane.setColumnIndex(txTextField, 1);
        gridPane.getChildren().add(txTextField);

        return new Tuple2<>(label, txTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, InputTextField> addLabelInputTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelInputTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, InputTextField> addLabelInputTextField(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        InputTextField inputTextField = new InputTextField();
        GridPane.setRowIndex(inputTextField, rowIndex);
        GridPane.setColumnIndex(inputTextField, 1);
        GridPane.setMargin(inputTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(inputTextField);

        return new Tuple2<>(label, inputTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + PasswordField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, PasswordTextField> addLabelPasswordTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelPasswordTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, PasswordTextField> addLabelPasswordTextField(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        PasswordTextField passwordField = new PasswordTextField();
        GridPane.setRowIndex(passwordField, rowIndex);
        GridPane.setColumnIndex(passwordField, 1);
        GridPane.setMargin(passwordField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(passwordField);

        return new Tuple2<>(label, passwordField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, InputTextField, CheckBox> addLabelInputTextFieldCheckBox(GridPane gridPane, int rowIndex, String title, String checkBoxTitle) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        InputTextField inputTextField = new InputTextField();
        CheckBox checkBox = new CheckBox(checkBoxTitle);
        HBox.setMargin(checkBox, new Insets(4, 0, 0, 0));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(inputTextField, checkBox);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, inputTextField, checkBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, InputTextField, Button> addLabelInputTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        InputTextField inputTextField = new InputTextField();
        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(inputTextField, button);
        HBox.setHgrow(inputTextField, Priority.ALWAYS);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, inputTextField, button);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, TextField, Button> addLabelTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle) {
        return addLabelTextFieldButton(gridPane, rowIndex, title, buttonTitle, 0);
    }

    public static Tuple3<Label, TextField, Button> addLabelTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TextField textField = new TextField();
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField, button);
        HBox.setHgrow(textField, Priority.ALWAYS);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, textField, button);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Label  + InputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple4<Label, InputTextField, Label, InputTextField> addLabelInputTextFieldLabelInputTextField(GridPane gridPane, int rowIndex, String title1, String title2) {
        Label label1 = addLabel(gridPane, rowIndex, title1, 0);

        InputTextField inputTextField1 = new InputTextField();
        Label label2 = new Label(title2);
        HBox.setMargin(label2, new Insets(5, 0, 0, 0));
        InputTextField inputTextField2 = new InputTextField();

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(inputTextField1, label2, inputTextField2);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(label1, inputTextField1, label2, inputTextField2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField + Label  + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple4<Label, TextField, Label, TextField> addLabelTextFieldLabelTextField(GridPane gridPane, int rowIndex, String title1, String title2) {
        Label label1 = addLabel(gridPane, rowIndex, title1, 0);

        TextField textField1 = new TextField();
        textField1.setEditable(false);
        textField1.setMouseTransparent(true);
        textField1.setFocusTraversable(false);
        Label label2 = new Label(title2);
        HBox.setMargin(label2, new Insets(5, 0, 0, 0));
        TextField textField2 = new TextField();
        textField2.setEditable(false);
        textField2.setMouseTransparent(true);
        textField2.setFocusTraversable(false);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField1, label2, textField2);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(label1, textField1, label2, textField2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Button, CheckBox> addButtonCheckBox(GridPane gridPane, int rowIndex, String buttonTitle, String checkBoxTitle) {
        return addButtonCheckBox(gridPane, rowIndex, buttonTitle, checkBoxTitle, 0);
    }

    public static Tuple2<Button, CheckBox> addButtonCheckBox(GridPane gridPane, int rowIndex, String buttonTitle, String checkBoxTitle, double top) {
        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);
        CheckBox checkBox = new CheckBox(checkBoxTitle);
        HBox.setMargin(checkBox, new Insets(6, 0, 0, 0));

        HBox hBox = new HBox();
        hBox.setSpacing(20);
        hBox.getChildren().addAll(button, checkBox);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        hBox.setPadding(new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple2<>(button, checkBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static CheckBox addCheckBox(GridPane gridPane, int rowIndex, String checkBoxTitle) {
        return addCheckBox(gridPane, rowIndex, checkBoxTitle, 0);
    }

    public static CheckBox addCheckBox(GridPane gridPane, int rowIndex, String checkBoxTitle, double top) {
        CheckBox checkBox = new CheckBox(checkBoxTitle);
        GridPane.setMargin(checkBox, new Insets(top, 0, 0, 0));
        GridPane.setRowIndex(checkBox, rowIndex);
        GridPane.setColumnIndex(checkBox, 1);
        gridPane.getChildren().add(checkBox);
        return checkBox;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RadioButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static RadioButton addRadioButton(GridPane gridPane, int rowIndex, ToggleGroup toggleGroup, String title) {
        RadioButton radioButton = new RadioButton(title);
        radioButton.setToggleGroup(toggleGroup);
        GridPane.setRowIndex(radioButton, rowIndex);
        GridPane.setColumnIndex(radioButton, 1);
        gridPane.getChildren().add(radioButton);
        return radioButton;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + RadioButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, RadioButton> addLabelRadioButton(GridPane gridPane, int rowIndex, ToggleGroup toggleGroup, String title, String
            radioButtonTitle) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        RadioButton radioButton = new RadioButton(radioButtonTitle);
        radioButton.setToggleGroup(toggleGroup);
        radioButton.setPadding(new Insets(6, 0, 0, 0));
        GridPane.setRowIndex(radioButton, rowIndex);
        GridPane.setColumnIndex(radioButton, 1);
        gridPane.getChildren().add(radioButton);

        return new Tuple2<>(label, radioButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + RadioButton + RadioButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, RadioButton, RadioButton> addLabelRadioButtonRadioButton(GridPane gridPane,
                                                                                         int rowIndex,
                                                                                         ToggleGroup toggleGroup,
                                                                                         String title,
                                                                                         String radioButtonTitle1,
                                                                                         String radioButtonTitle2) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        RadioButton radioButton1 = new RadioButton(radioButtonTitle1);
        radioButton1.setToggleGroup(toggleGroup);
        radioButton1.setPadding(new Insets(6, 0, 0, 0));

        RadioButton radioButton2 = new RadioButton(radioButtonTitle2);
        radioButton2.setToggleGroup(toggleGroup);
        radioButton2.setPadding(new Insets(6, 0, 0, 0));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(radioButton1, radioButton2);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, radioButton1, radioButton2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, CheckBox> addLabelCheckBox(GridPane gridPane, int rowIndex, String title) {
        return addLabelCheckBox(gridPane, rowIndex, title, "", 0);
    }

    public static Tuple2<Label, CheckBox> addLabelCheckBox(GridPane gridPane, int rowIndex, String title, String checkBoxTitle) {
        return addLabelCheckBox(gridPane, rowIndex, title, checkBoxTitle, 0);
    }

    public static Tuple2<Label, CheckBox> addLabelCheckBox(GridPane gridPane, int rowIndex, String title, String checkBoxTitle, double top) {
        Label label = addLabel(gridPane, rowIndex, title, -3);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));

        CheckBox checkBox = new CheckBox(checkBoxTitle);
        GridPane.setRowIndex(checkBox, rowIndex);
        GridPane.setColumnIndex(checkBox, 1);
        GridPane.setMargin(checkBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(checkBox);

        return new Tuple2<>(label, checkBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, ComboBox> addLabelComboBox(GridPane gridPane, int rowIndex) {
        return addLabelComboBox(gridPane, rowIndex, null, 0);
    }

    public static Tuple2<Label, ComboBox> addLabelComboBox(GridPane gridPane, int rowIndex, String title) {
        return addLabelComboBox(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, ComboBox> addLabelComboBox(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = null;
        if (title != null)
            label = addLabel(gridPane, rowIndex, title, top);

        ComboBox comboBox = new ComboBox();
        GridPane.setRowIndex(comboBox, rowIndex);
        GridPane.setColumnIndex(comboBox, 1);
        GridPane.setMargin(comboBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(comboBox);

        return new Tuple2<>(label, comboBox);
    }

    public static Tuple2<Label, SearchComboBox> addLabelSearchComboBox(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = null;
        if (title != null)
            label = addLabel(gridPane, rowIndex, title, top);

        SearchComboBox comboBox = new SearchComboBox();
        GridPane.setRowIndex(comboBox, rowIndex);
        GridPane.setColumnIndex(comboBox, 1);
        GridPane.setMargin(comboBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(comboBox);

        return new Tuple2<>(label, comboBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + ComboBox + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, ComboBox, ComboBox> addLabelComboBoxComboBox(GridPane gridPane, int rowIndex, String title) {
        return addLabelComboBoxComboBox(gridPane, rowIndex, title, 0);
    }

    public static Tuple3<Label, ComboBox, ComboBox> addLabelComboBoxComboBox(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        ComboBox comboBox1 = new ComboBox();
        ComboBox comboBox2 = new ComboBox();
        hBox.getChildren().addAll(comboBox1, comboBox2);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, comboBox1, comboBox2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ComboBox + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, ComboBox, Button> addLabelComboBoxButton(GridPane gridPane,
                                                                         int rowIndex,
                                                                         String title,
                                                                         String buttonTitle) {
        return addLabelComboBoxButton(gridPane, rowIndex, title, buttonTitle, 0);
    }

    public static Tuple3<Label, ComboBox, Button> addLabelComboBoxButton(GridPane gridPane,
                                                                         int rowIndex,
                                                                         String title,
                                                                         String buttonTitle,
                                                                         double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);

        ComboBox comboBox = new ComboBox();

        hBox.getChildren().addAll(comboBox, button);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, comboBox, button);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ComboBox + Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, ComboBox, TextField> addLabelComboBoxLabel(GridPane gridPane,
                                                                           int rowIndex,
                                                                           String title,
                                                                           String textFieldText) {
        return addLabelComboBoxLabel(gridPane, rowIndex, title, textFieldText, 0);
    }

    public static Tuple3<Label, ComboBox, TextField> addLabelComboBoxLabel(GridPane gridPane,
                                                                           int rowIndex,
                                                                           String title,
                                                                           String textFieldText,
                                                                           double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        ComboBox comboBox = new ComboBox();
        TextField textField = new TextField(textFieldText);
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);

        hBox.getChildren().addAll(comboBox, textField);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(label, comboBox, textField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TxIdTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelTxIdTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TxIdTextField txIdTextField = new TxIdTextField();
        GridPane.setRowIndex(txIdTextField, rowIndex);
        GridPane.setColumnIndex(txIdTextField, 1);
        GridPane.setMargin(txIdTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(txIdTextField);

        return new Tuple2<>(label, txIdTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextFieldWithCopyIcon
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextFieldWithCopyIcon> addLabelTextFieldWithCopyIcon(GridPane gridPane, int rowIndex, String title, String value) {
        return addLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addLabelTextFieldWithCopyIcon(GridPane gridPane, int rowIndex, String title, String value, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon();
        textFieldWithCopyIcon.setText(value);
        GridPane.setRowIndex(textFieldWithCopyIcon, rowIndex);
        GridPane.setColumnIndex(textFieldWithCopyIcon, 1);
        GridPane.setMargin(textFieldWithCopyIcon, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textFieldWithCopyIcon);

        return new Tuple2<>(label, textFieldWithCopyIcon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + AddressTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, AddressTextField> addLabelAddressTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelAddressTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, AddressTextField> addLabelAddressTextField(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        AddressTextField addressTextField = new AddressTextField();
        GridPane.setRowIndex(addressTextField, rowIndex);
        GridPane.setColumnIndex(addressTextField, 1);
        GridPane.setMargin(addressTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(addressTextField);

        return new Tuple2<>(label, addressTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + BsqAddressTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, BsqAddressTextField> addLabelBsqAddressTextField(GridPane gridPane, int rowIndex, String title) {
        return addLabelBsqAddressTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, BsqAddressTextField> addLabelBsqAddressTextField(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        BsqAddressTextField addressTextField = new BsqAddressTextField();
        GridPane.setRowIndex(addressTextField, rowIndex);
        GridPane.setColumnIndex(addressTextField, 1);
        GridPane.setMargin(addressTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(addressTextField);

        return new Tuple2<>(label, addressTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + BalanceTextField
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static Tuple2<Label, BalanceTextField> addLabelBalanceTextField(GridPane gridPane, int rowIndex, String title) {
        Label label = addLabel(gridPane, rowIndex, title, 0);

        BalanceTextField balanceTextField = new BalanceTextField();
        GridPane.setRowIndex(balanceTextField, rowIndex);
        GridPane.setColumnIndex(balanceTextField, 1);
        gridPane.getChildren().add(balanceTextField);

        return new Tuple2<>(label, balanceTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, Button> addLabelButton(GridPane gridPane, int rowIndex, String labelText, String buttonTitle) {
        return addLabelButton(gridPane, rowIndex, labelText, buttonTitle, 0);
    }

    public static Tuple2<Label, Button> addLabelButton(GridPane gridPane, int rowIndex, String labelText, String buttonTitle, double top) {
        Label label = addLabel(gridPane, rowIndex, labelText, top);

        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);
        GridPane.setRowIndex(button, rowIndex);
        GridPane.setColumnIndex(button, 1);
        gridPane.getChildren().add(button);
        GridPane.setMargin(button, new Insets(top, 0, 0, 0));
        return new Tuple2<>(label, button);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Button addButton(GridPane gridPane, int rowIndex, String title) {
        return addButton(gridPane, rowIndex, title, 0);
    }

    public static Button addButtonAfterGroup(GridPane gridPane, int rowIndex, String title) {
        return addButton(gridPane, rowIndex, title, 15);
    }

    public static Button addButton(GridPane gridPane, int rowIndex, String title, double top) {
        Button button = new Button(title);
        button.setDefaultButton(true);
        GridPane.setRowIndex(button, rowIndex);
        GridPane.setColumnIndex(button, 1);
        gridPane.getChildren().add(button);
        GridPane.setMargin(button, new Insets(top, 0, 0, 0));
        return button;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Button, Button> add2Buttons(GridPane gridPane,
                                                     int rowIndex,
                                                     String title1,
                                                     String title2) {
        return add2Buttons(gridPane, rowIndex, title1, title2, 0);
    }

    public static Tuple2<Button, Button> add2ButtonsAfterGroup(GridPane gridPane,
                                                               int rowIndex,
                                                               String title1,
                                                               String title2) {
        return add2Buttons(gridPane, rowIndex, title1, title2, 15);
    }

    public static Tuple2<Button, Button> add2Buttons(GridPane gridPane,
                                                     int rowIndex,
                                                     String title1,
                                                     String title2,
                                                     double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        Button button1 = new Button(title1);
        button1.setDefaultButton(true);
        Button button2 = new Button(title2);
        hBox.getChildren().addAll(button1, button2);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 10, 0, 0));
        gridPane.getChildren().add(hBox);
        return new Tuple2<>(button1, button2);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button + Button + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Button, Button, Button> add3Buttons(GridPane gridPane,
                                                             int rowIndex,
                                                             String title1,
                                                             String title2,
                                                             String title3) {
        return add3Buttons(gridPane, rowIndex, title1, title2, title3, 0);
    }

    public static Tuple3<Button, Button, Button> add3ButtonsAfterGroup(GridPane gridPane,
                                                                       int rowIndex,
                                                                       String title1,
                                                                       String title2,
                                                                       String title3) {
        return add3Buttons(gridPane, rowIndex, title1, title2, title3, 15);
    }

    public static Tuple3<Button, Button, Button> add3Buttons(GridPane gridPane,
                                                             int rowIndex,
                                                             String title1,
                                                             String title2,
                                                             String title3,
                                                             double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);
        Button button1 = new Button(title1);
        button1.setDefaultButton(true);
        Button button2 = new Button(title2);
        Button button3 = new Button(title3);
        hBox.getChildren().addAll(button1, button2, button3);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 10, 0, 0));
        gridPane.getChildren().add(hBox);
        return new Tuple3<>(button1, button2, button3);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button + ProgressIndicator + Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Button, BusyAnimation, Label> addButtonBusyAnimationLabelAfterGroup(GridPane gridPane,
                                                                                             int rowIndex,
                                                                                             String buttonTitle) {
        return addButtonBusyAnimationLabel(gridPane, rowIndex, buttonTitle, 15);
    }

    public static Tuple3<Button, BusyAnimation, Label> addButtonBusyAnimationLabel(GridPane gridPane,
                                                                                   int rowIndex,
                                                                                   String buttonTitle,
                                                                                   double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        Button button = new Button(buttonTitle);
        button.setDefaultButton(true);

        BusyAnimation busyAnimation = new BusyAnimation(false);

        Label label = new Label();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(button, busyAnimation, label);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 1);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(button, busyAnimation, label);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade: HBox, InputTextField, Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<HBox, InputTextField, Label> getEditableValueCurrencyBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPrefWidth(170);
        input.setAlignment(Pos.CENTER_RIGHT);
        input.setId("text-input-with-currency-text-field");
        input.setPromptText(promptText);

        Label currency = new Label(Res.getBaseCurrencyCode());
        currency.setId("currency-info-label");

        HBox box = new HBox();
        box.getChildren().addAll(input, currency);
        return new Tuple3<>(box, input, currency);
    }

    public static Tuple3<HBox, TextField, Label> getNonEditableValueCurrencyBox() {
        TextField textField = new InputTextField();
        textField.setPrefWidth(190);
        textField.setAlignment(Pos.CENTER_RIGHT);
        textField.setId("text-input-with-currency-text-field");
        textField.setMouseTransparent(true);
        textField.setEditable(false);
        textField.setFocusTraversable(false);

        Label currency = new Label(Res.getBaseCurrencyCode());
        currency.setId("currency-info-label-disabled");

        HBox box = new HBox();
        box.getChildren().addAll(textField, currency);
        return new Tuple3<>(box, textField, currency);
    }

    public static Tuple3<HBox, InputTextField, Label> getAmountCurrencyBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPrefWidth(190);
        input.setAlignment(Pos.CENTER_RIGHT);
        input.setId("text-input-with-currency-text-field");
        input.setPromptText(promptText);

        Label currency = new Label(Res.getBaseCurrencyCode());
        currency.setId("currency-info-label");

        HBox box = new HBox();
        box.getChildren().addAll(input, currency);
        return new Tuple3<>(box, input, currency);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade: Label, VBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox) {
        return getTradeInputBox(amountValueBox, "");
    }

    public static Tuple2<Label, VBox> getTradeInputBox(HBox amountValueBox, String descriptionText) {
        Label descriptionLabel = new Label(descriptionText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(170);

        VBox box = new VBox();
        box.setSpacing(2);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + List
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, ListView> addLabelListView(GridPane gridPane, int rowIndex, String title) {
        return addLabelListView(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, ListView> addLabelListView(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        ListView listView = new ListView();
        GridPane.setRowIndex(listView, rowIndex);
        GridPane.setColumnIndex(listView, 1);
        GridPane.setMargin(listView, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(listView);

        return new Tuple2<>(label, listView);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Remove
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static void removeRowFromGridPane(GridPane gridPane, int gridRow) {
        removeRowsFromGridPane(gridPane, gridRow, gridRow);
    }

    public static void removeRowsFromGridPane(GridPane gridPane, int fromGridRow, int toGridRow) {
        Set<Node> nodes = new CopyOnWriteArraySet<>(gridPane.getChildren());
        nodes.stream()
                .filter(e -> GridPane.getRowIndex(e) >= fromGridRow && GridPane.getRowIndex(e) <= toGridRow)
                .forEach(e -> gridPane.getChildren().remove(e));
    }


}
