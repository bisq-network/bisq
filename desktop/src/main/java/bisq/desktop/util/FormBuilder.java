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

package bisq.desktop.util;

import bisq.desktop.components.AddressTextField;
import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipCheckBox;
import bisq.desktop.components.AutoTooltipLabel;
import bisq.desktop.components.AutoTooltipRadioButton;
import bisq.desktop.components.AutoTooltipSlideToggleButton;
import bisq.desktop.components.BalanceTextField;
import bisq.desktop.components.BsqAddressTextField;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InfoTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.components.SearchComboBox;
import bisq.desktop.components.TextFieldWithCopyIcon;
import bisq.desktop.components.TextFieldWithIcon;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.components.TxIdTextField;

import bisq.core.locale.Res;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;
import bisq.common.util.Tuple4;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;
import de.jensd.fx.glyphs.GlyphIcons;
import de.jensd.fx.glyphs.materialdesignicons.utils.MaterialDesignIconFactory;

import com.jfoenix.controls.JFXComboBox;
import com.jfoenix.controls.JFXDatePicker;
import com.jfoenix.controls.JFXTextArea;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.text.Text;

import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

import org.jetbrains.annotations.NotNull;

import static bisq.desktop.util.GUIUtil.getComboBoxButtonCell;

public class FormBuilder {
    public static final String MATERIAL_DESIGN_ICONS = "'Material Design Icons'";
    public static final String FONTAWESOME_ICONS = "FontAwesome";


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
        Label label = new AutoTooltipLabel(title);
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
        Label label = new AutoTooltipLabel(text);
        label.setWrapText(true);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setColumnSpan(label, 2);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TextField addTextField(GridPane gridPane, int rowIndex, String title, String value) {
        return addTextField(gridPane, rowIndex, title, value, 0);
    }

    public static TextField addTextField(GridPane gridPane, int rowIndex, String title, String value, double top) {
        TextField textField = new JFXTextField(value);
        ((JFXTextField) textField).setLabelFloat(true);
        textField.setPromptText(title);
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);

        GridPane.setRowIndex(textField, rowIndex);
        GridPane.setMargin(textField, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(textField);

        return textField;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane, int rowIndex, String title) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", 0);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane, int rowIndex, String title, String value) {
        return addTopLabelTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane, int rowIndex, String title, double top) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", top);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane, int rowIndex, String title, String value, double top) {
        TextField textField = new JFXTextField(value);
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textField, top);

        return new Tuple3<>(topLabelWithVBox.first, textField, topLabelWithVBox.second);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextFieldWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static Tuple2<Label, TextFieldWithIcon> addLabelTextFieldWithIcon(GridPane gridPane, int rowIndex, String title, double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TextFieldWithIcon textFieldWithIcon = new TextFieldWithIcon();
        textFieldWithIcon.setMouseTransparent(true);
        textFieldWithIcon.setFocusTraversable(false);
        GridPane.setRowIndex(textFieldWithIcon, rowIndex);
        GridPane.setColumnIndex(textFieldWithIcon, 1);
        GridPane.setMargin(textFieldWithIcon, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(textFieldWithIcon);

        return new Tuple2<>(label, textFieldWithIcon);
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
        GridPane.setHalignment(hyperlinkWithIcon, HPos.LEFT);
        gridPane.getChildren().add(hyperlinkWithIcon);
        return hyperlinkWithIcon;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + HyperlinkWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, HyperlinkWithIcon> addLabelHyperlinkWithIcon(GridPane gridPane,
                                                                             int rowIndex,
                                                                             String labelTitle,
                                                                             String title,
                                                                             String url) {
        return addLabelHyperlinkWithIcon(gridPane, rowIndex, labelTitle, title, url, 0);
    }

    public static Tuple2<Label, HyperlinkWithIcon> addLabelHyperlinkWithIcon(GridPane gridPane,
                                                                             int rowIndex,
                                                                             String labelTitle,
                                                                             String title,
                                                                             String url,
                                                                             double top) {
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
    // TextArea
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TextArea addTextArea(GridPane gridPane, int rowIndex, String prompt) {
        return addTextArea(gridPane, rowIndex, prompt, 0);
    }

    public static TextArea addTextArea(GridPane gridPane, int rowIndex, String prompt, double top) {

        TextArea textArea = new JFXTextArea();
        textArea.setPromptText(prompt);
        ((JFXTextArea) textArea).setLabelFloat(true);
        textArea.setWrapText(true);

        GridPane.setRowIndex(textArea, rowIndex);
        GridPane.setColumnIndex(textArea, 0);
        GridPane.setMargin(textArea, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(textArea);

        return textArea;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextArea
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane, int rowIndex, String title, String prompt) {
        return addTopLabelTextArea(gridPane, rowIndex, title, prompt, 0);
    }

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane, int rowIndex, String title, String prompt, double top) {

        TextArea textArea = new JFXTextArea();
        textArea.setPromptText(prompt);
        ((JFXTextArea) textArea).setLabelFloat(true);
        textArea.setWrapText(true);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textArea, top);

        return new Tuple2<>(topLabelWithVBox.first, textArea);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + DatePicker
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, DatePicker> addTopLabelDatePicker(GridPane gridPane, int rowIndex, String title, double top) {
        DatePicker datePicker = new JFXDatePicker();

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, datePicker, top);

        return new Tuple2<>(topLabelWithVBox.first, datePicker);
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

    public static InputTextField addInputTextField(GridPane gridPane, int rowIndex, String title) {
        return addInputTextField(gridPane, rowIndex, title, 0);
    }

    public static InputTextField addInputTextField(GridPane gridPane, int rowIndex, String title, double top) {

        InputTextField inputTextField = new InputTextField();
        inputTextField.setLabelFloat(true);
        inputTextField.setPromptText(title);
        GridPane.setRowIndex(inputTextField, rowIndex);
        GridPane.setColumnIndex(inputTextField, 0);
        GridPane.setMargin(inputTextField, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(inputTextField);

        return inputTextField;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InfoInputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, InfoInputTextField> addTopLabelInfoInputTextField(GridPane gridPane, int rowIndex, String title) {
        return addTopLabelInfoInputTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, InfoInputTextField> addTopLabelInfoInputTextField(GridPane gridPane, int rowIndex, String title, double top) {

        InfoInputTextField inputTextField = new InfoInputTextField();

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, inputTextField, top);

        return new Tuple2<>(topLabelWithVBox.first, inputTextField);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PasswordField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static PasswordTextField addPasswordTextField(GridPane gridPane, int rowIndex, String title) {
        return addPasswordTextField(gridPane, rowIndex, title, 0);
    }

    public static PasswordTextField addPasswordTextField(GridPane gridPane, int rowIndex, String title, double top) {
        PasswordTextField passwordField = new PasswordTextField();
        passwordField.setPromptText(title);
        GridPane.setRowIndex(passwordField, rowIndex);
        GridPane.setColumnIndex(passwordField, 0);
        GridPane.setColumnSpan(passwordField, 2);
        GridPane.setMargin(passwordField, new Insets(top + 10, 0, 20, 0));
        gridPane.getChildren().add(passwordField);

        return passwordField;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, InputTextField, ToggleButton> addTopLabelInputTextFieldSlideToggleButton(GridPane gridPane, int rowIndex, String title, String toggleButtonTitle) {

        InputTextField inputTextField = new InputTextField();
        ToggleButton toggleButton = new JFXToggleButton();
        toggleButton.setText(toggleButtonTitle);
        VBox.setMargin(toggleButton, new Insets(4, 0, 0, 0));

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, inputTextField, 0);

        topLabelWithVBox.second.getChildren().add(toggleButton);

        return new Tuple3<>(topLabelWithVBox.first, inputTextField, toggleButton);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, InputTextField, Button> addTopLabelInputTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle) {
        InputTextField inputTextField = new InputTextField();
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(inputTextField, button);
        HBox.setHgrow(inputTextField, Priority.ALWAYS);

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, 0);

        return new Tuple3<>(labelVBoxTuple2.first, inputTextField, button);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, TextField, Button> addTopLabelTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle) {
        return addTopLabelTextFieldButton(gridPane, rowIndex, title, buttonTitle, 0);
    }

    public static Tuple3<Label, TextField, Button> addTopLabelTextFieldButton(GridPane gridPane, int rowIndex, String title, String buttonTitle, double top) {

        TextField textField = new JFXTextField();
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField, button);
        HBox.setHgrow(textField, Priority.ALWAYS);

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, 0);

        return new Tuple3<>(labelVBoxTuple2.first, textField, button);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Label  + InputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<InputTextField, InputTextField> addInputTextFieldInputTextField(GridPane gridPane, int rowIndex, String title1, String title2) {

        InputTextField inputTextField1 = new InputTextField();
        inputTextField1.setPromptText(title1);
        inputTextField1.setLabelFloat(true);
        InputTextField inputTextField2 = new InputTextField();
        inputTextField2.setLabelFloat(true);
        inputTextField2.setPromptText(title2);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(inputTextField1, inputTextField2);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setMargin(hBox, new Insets(Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple2<>(inputTextField1, inputTextField2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField + Label  + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple4<Label, TextField, Label, TextField> addLabelTextFieldLabelTextField(GridPane gridPane, int rowIndex, String title1, String title2) {
        Label label1 = addLabel(gridPane, rowIndex, title1, 0);

        TextField textField1 = new JFXTextField();
        textField1.setEditable(false);
        textField1.setMouseTransparent(true);
        textField1.setFocusTraversable(false);
        Label label2 = new AutoTooltipLabel(title2);
        HBox.setMargin(label2, new Insets(5, 0, 0, 0));
        TextField textField2 = new JFXTextField();
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
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);
        CheckBox checkBox = new AutoTooltipCheckBox(checkBoxTitle);
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
        CheckBox checkBox = new AutoTooltipCheckBox(checkBoxTitle);
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
        RadioButton radioButton = new AutoTooltipRadioButton(title);
        radioButton.setToggleGroup(toggleGroup);
        GridPane.setRowIndex(radioButton, rowIndex);
        GridPane.setColumnIndex(radioButton, 1);
        gridPane.getChildren().add(radioButton);
        return radioButton;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + RadioButton + RadioButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, RadioButton, RadioButton> addTopLabelRadioButtonRadioButton(GridPane gridPane,
                                                                                            int rowIndex,
                                                                                            ToggleGroup toggleGroup,
                                                                                            String title,
                                                                                            String radioButtonTitle1,
                                                                                            String radioButtonTitle2,
                                                                                            int top) {
        RadioButton radioButton1 = new AutoTooltipRadioButton(radioButtonTitle1);
        radioButton1.setToggleGroup(toggleGroup);
        radioButton1.setPadding(new Insets(6, 0, 0, 0));

        RadioButton radioButton2 = new AutoTooltipRadioButton(radioButtonTitle2);
        radioButton2.setToggleGroup(toggleGroup);
        radioButton2.setPadding(new Insets(6, 0, 0, 0));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(radioButton1, radioButton2);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, top);

        return new Tuple3<>(topLabelWithVBox.first, radioButton1, radioButton2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static CheckBox addLabelCheckBox(GridPane gridPane, int rowIndex, String title) {
        return addLabelCheckBox(gridPane, rowIndex, title, 0);
    }

    public static CheckBox addLabelCheckBox(GridPane gridPane, int rowIndex, String title, double top) {
        CheckBox checkBox = new AutoTooltipCheckBox(title);
        GridPane.setRowIndex(checkBox, rowIndex);
        GridPane.setColumnIndex(checkBox, 0);
        GridPane.setMargin(checkBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(checkBox);

        return checkBox;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // SlideToggleButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static ToggleButton addSlideToggleButton(GridPane gridPane, int rowIndex, String title) {
        return addSlideToggleButton(gridPane, rowIndex, title, 0);
    }

    public static ToggleButton addSlideToggleButton(GridPane gridPane, int rowIndex, String title, double top) {
        ToggleButton toggleButton = new AutoTooltipSlideToggleButton();
        toggleButton.setText(title);
        GridPane.setRowIndex(toggleButton, rowIndex);
        GridPane.setColumnIndex(toggleButton, 0);
        GridPane.setMargin(toggleButton, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(toggleButton);

        return toggleButton;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> ComboBox<T> addComboBox(GridPane gridPane, int rowIndex, int top) {
        final JFXComboBox<T> comboBox = new JFXComboBox<>();

        GridPane.setRowIndex(comboBox, rowIndex);
        GridPane.setMargin(comboBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(comboBox);
        return comboBox;

    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple2<Label, ComboBox<T>> addTopLabelComboBox(GridPane gridPane, int rowIndex, String title, String prompt, int top) {
        final Tuple3<VBox, Label, ComboBox<T>> tuple3 = addTopLabelComboBox(title, prompt, 0);
        final VBox vBox = tuple3.first;

        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setMargin(vBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(vBox);

        return new Tuple2<>(tuple3.second, tuple3.third);
    }

    public static <T> Tuple3<VBox, Label, ComboBox<T>> addTopLabelComboBox(String title, String prompt) {
        return addTopLabelComboBox(title, prompt, 0);
    }

    public static <T> Tuple3<VBox, Label, ComboBox<T>> addTopLabelComboBox(String title, String prompt, int top) {
        Label label = getTopLabel(title);
        VBox vBox = getTopLabelVBox(top);

        final JFXComboBox<T> comboBox = new JFXComboBox<>();
        comboBox.setPromptText(prompt);

        vBox.getChildren().addAll(label, comboBox);

        return new Tuple3<>(vBox, label, comboBox);
    }

    @NotNull
    private static VBox getTopLabelVBox(int top) {
        VBox vBox = new VBox();
        vBox.setSpacing(0);
        vBox.setPadding(new Insets(top, 0, 0, 0));
        vBox.setAlignment(Pos.CENTER_LEFT);
        return vBox;
    }

    @NotNull
    private static Label getTopLabel(String title) {
        Label label = new AutoTooltipLabel(title);
        label.getStyleClass().add("small-text");
        return label;
    }

    @NotNull
    public static Tuple2<Label, VBox> addTopLabelWithVBox(GridPane gridPane, int rowIndex,
                                                          String title, Node node,
                                                          double top) {
        Label label = getTopLabel(title);
        VBox vBox = getTopLabelVBox(0);
        vBox.getChildren().addAll(label, node);

        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setColumnIndex(vBox, 0);
        GridPane.setMargin(vBox, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(vBox);

        return new Tuple2<>(label, vBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> ComboBox<T> addComboBox(GridPane gridPane, int rowIndex) {
        return addComboBox(gridPane, rowIndex, null, 0);
    }

    public static <T> ComboBox<T> addComboBox(GridPane gridPane, int rowIndex, String title) {
        return addComboBox(gridPane, rowIndex, title, 0);
    }

    public static <T> ComboBox<T> addComboBox(GridPane gridPane, int rowIndex, String title, double top) {
        JFXComboBox<T> comboBox = new JFXComboBox<>();
        comboBox.setLabelFloat(true);
        comboBox.setPromptText(title);
        comboBox.setMaxWidth(Double.MAX_VALUE);

        // Default ComboBox does not show promptText after clear selection.
        // https://stackoverflow.com/questions/50569330/how-to-reset-combobox-and-display-prompttext?noredirect=1&lq=1
        comboBox.setButtonCell(getComboBoxButtonCell(title, comboBox));

        GridPane.setRowIndex(comboBox, rowIndex);
        GridPane.setColumnIndex(comboBox, 0);
        GridPane.setMargin(comboBox, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(comboBox);

        return comboBox;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + SearchComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple2<Label, ComboBox<T>> addLabelSearchComboBox(GridPane gridPane, int rowIndex, String title, double top) {

        SearchComboBox<T> comboBox = new SearchComboBox<>();

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, comboBox, top);

        return new Tuple2<>(labelVBoxTuple2.first, comboBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + ComboBox + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T, R> Tuple3<Label, ComboBox<R>, ComboBox<T>> addTopLabelComboBoxComboBox(GridPane gridPane, int rowIndex, String title) {
        return addTopLabelComboBoxComboBox(gridPane, rowIndex, title, 0);
    }

    public static <T, R> Tuple3<Label, ComboBox<T>, ComboBox<R>> addTopLabelComboBoxComboBox(GridPane gridPane, int rowIndex, String title, double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        ComboBox<T> comboBox1 = new JFXComboBox<>();
        ComboBox<R> comboBox2 = new JFXComboBox<>();
        hBox.getChildren().addAll(comboBox1, comboBox2);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, top);

        return new Tuple3<>(topLabelWithVBox.first, comboBox1, comboBox2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ComboBox + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple3<Label, ComboBox<T>, Button> addLabelComboBoxButton(GridPane gridPane,
                                                                                int rowIndex,
                                                                                String title,
                                                                                String buttonTitle) {
        return addLabelComboBoxButton(gridPane, rowIndex, title, buttonTitle, 0);
    }

    public static <T> Tuple3<Label, ComboBox<T>, Button> addLabelComboBoxButton(GridPane gridPane,
                                                                                int rowIndex,
                                                                                String title,
                                                                                String buttonTitle,
                                                                                double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        ComboBox<T> comboBox = new JFXComboBox<>();

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

    public static <T> Tuple3<Label, ComboBox<T>, TextField> addLabelComboBoxLabel(GridPane gridPane,
                                                                                  int rowIndex,
                                                                                  String title,
                                                                                  String textFieldText) {
        return addLabelComboBoxLabel(gridPane, rowIndex, title, textFieldText, 0);
    }

    public static <T> Tuple3<Label, ComboBox<T>, TextField> addLabelComboBoxLabel(GridPane gridPane,
                                                                                  int rowIndex,
                                                                                  String title,
                                                                                  String textFieldText,
                                                                                  double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        HBox hBox = new HBox();
        hBox.setSpacing(10);

        ComboBox<T> comboBox = new JFXComboBox<>();
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

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane, int rowIndex, String title, String value) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane, int rowIndex, String title, String value, double top) {

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon();
        textFieldWithCopyIcon.setText(value);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textFieldWithCopyIcon, top);

        return new Tuple2<>(topLabelWithVBox.first, textFieldWithCopyIcon);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + AddressTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static AddressTextField addAddressTextField(GridPane gridPane, int rowIndex, String title) {
        return addAddressTextField(gridPane, rowIndex, title, 0);
    }

    public static AddressTextField addAddressTextField(GridPane gridPane, int rowIndex, String title, double top) {
        AddressTextField addressTextField = new AddressTextField(title);
        GridPane.setRowIndex(addressTextField, rowIndex);
        GridPane.setColumnIndex(addressTextField, 0);
        GridPane.setMargin(addressTextField, new Insets(top + 20, 0, 0, 0));
        gridPane.getChildren().add(addressTextField);

        return addressTextField;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + FundsTextField
    ///////////////////////////////////////////////////////////////////////////////////////////
    public static FundsTextField addFundsTextfield(GridPane gridPane, int rowIndex, String text) {
        return addFundsTextfield(gridPane, rowIndex, text, 0);
    }

    public static FundsTextField addFundsTextfield(GridPane gridPane, int rowIndex, String text, double top) {

        FundsTextField fundsTextField = new FundsTextField();
        fundsTextField.getTextField().setPromptText(text);
        GridPane.setRowIndex(fundsTextField, rowIndex);
        GridPane.setColumnIndex(fundsTextField, 0);
        GridPane.setMargin(fundsTextField, new Insets(top + 20, 0, 0, 0));
        gridPane.getChildren().add(fundsTextField);

        return fundsTextField;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InfoTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, InfoTextField> addLabelInfoTextfield(GridPane gridPane, int rowIndex, String labelText,
                                                                     String fieldText) {
        return addLabelInfoTextfield(gridPane, rowIndex, labelText, fieldText, 0);
    }

    public static Tuple2<Label, InfoTextField> addLabelInfoTextfield(GridPane gridPane, int rowIndex, String labelText,
                                                                     String fieldText, double top) {
        Label label = addLabel(gridPane, rowIndex, labelText, top);

        InfoTextField infoTextField = new InfoTextField();
        infoTextField.setText(fieldText);
        GridPane.setRowIndex(infoTextField, rowIndex);
        GridPane.setColumnIndex(infoTextField, 1);
        GridPane.setMargin(infoTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(infoTextField);

        return new Tuple2<>(label, infoTextField);
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


    public static BalanceTextField addBalanceTextField(GridPane gridPane, int rowIndex, String title) {

        BalanceTextField balanceTextField = new BalanceTextField(title);
        GridPane.setRowIndex(balanceTextField, rowIndex);
        GridPane.setColumnIndex(balanceTextField, 0);
        GridPane.setMargin(balanceTextField, new Insets(20, 0, 0, 0));
        gridPane.getChildren().add(balanceTextField);

        return balanceTextField;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, Button> addTopLabelButton(GridPane gridPane, int rowIndex, String labelText, String buttonTitle) {
        return addTopLabelButton(gridPane, rowIndex, labelText, buttonTitle, 0);
    }

    public static Tuple2<Label, Button> addTopLabelButton(GridPane gridPane, int rowIndex, String labelText, String buttonTitle, double top) {
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, labelText, button, top);

        return new Tuple2<>(topLabelWithVBox.first, button);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + Button + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, Button, Button> addTopLabel2Buttons(GridPane gridPane, int rowIndex, String labelText, String title1, String title2, double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        Button button1 = new AutoTooltipButton(title1);
        button1.setDefaultButton(true);
        button1.getStyleClass().add("action-button");
        button1.setDefaultButton(true);
        button1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button1, Priority.ALWAYS);

        Button button2 = new AutoTooltipButton(title2);
        button2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button2, Priority.ALWAYS);

        hBox.getChildren().addAll(button1, button2);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, labelText, hBox, top);

        return new Tuple3<>(topLabelWithVBox.first, button1, button2);
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

    public static Button addPrimaryActionButton(GridPane gridPane, int rowIndex, String title, double top) {
        return addButton(gridPane, rowIndex, title, top, true);
    }

    public static Button addButton(GridPane gridPane, int rowIndex, String title, double top) {
        return addButton(gridPane, rowIndex, title, top, false);
    }

    public static Button addButton(GridPane gridPane, int rowIndex, String title, double top, boolean isPrimaryAction) {
        Button button = new AutoTooltipButton(title);
        if (isPrimaryAction) {
            button.setDefaultButton(true);
            button.getStyleClass().add("action-button");
        }

        GridPane.setRowIndex(button, rowIndex);
        GridPane.setColumnIndex(button, 0);
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
        return add2ButtonsAfterGroup(gridPane, rowIndex, title1, title2, true);
    }

    public static Tuple2<Button, Button> add2ButtonsAfterGroup(GridPane gridPane,
                                                               int rowIndex,
                                                               String title1,
                                                               String title2,
                                                               boolean hasPrimaryButton) {
        return add2Buttons(gridPane, rowIndex, title1, title2, 15, hasPrimaryButton);
    }

    public static Tuple2<Button, Button> add2Buttons(GridPane gridPane,
                                                     int rowIndex,
                                                     String title1,
                                                     String title2,
                                                     double top) {
        return add2Buttons(gridPane, rowIndex, title1, title2, top, true);
    }

    public static Tuple2<Button, Button> add2Buttons(GridPane gridPane, int rowIndex, String title1,
                                                     String title2, double top, boolean hasPrimaryButton) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        Button button1 = new AutoTooltipButton(title1);

        if (hasPrimaryButton) {
            button1.getStyleClass().add("action-button");
            button1.setDefaultButton(true);
        }

        button1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button1, Priority.ALWAYS);

        Button button2 = new AutoTooltipButton(title2);
        button2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button2, Priority.ALWAYS);

        hBox.getChildren().addAll(button1, button2);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 0);
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
        Button button1 = new AutoTooltipButton(title1);

        button1.getStyleClass().add("action-button");
        button1.setDefaultButton(true);
        button1.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button1, Priority.ALWAYS);

        Button button2 = new AutoTooltipButton(title2);
        button2.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button2, Priority.ALWAYS);

        Button button3 = new AutoTooltipButton(title3);
        button3.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button3, Priority.ALWAYS);

        hBox.getChildren().addAll(button1, button2, button3);
        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 0);
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

        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);
        button.getStyleClass().add("action-button");

        BusyAnimation busyAnimation = new BusyAnimation(false);

        Label label = new AutoTooltipLabel();
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(button, busyAnimation, label);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, 0);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(button, busyAnimation, label);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade: HBox, InputTextField, Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<HBox, InputTextField, Label> getEditableValueBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPromptText(promptText);

        Label label = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        label.getStyleClass().add("input-label");

        HBox box = new HBox();
        box.getStyleClass().add("input-with-border");
        box.setMaxWidth(243);
        box.getChildren().addAll(input, label);
        return new Tuple3<>(box, input, label);
    }

    public static Tuple3<HBox, InfoInputTextField, Label> getEditableValueBoxWithInfo(String promptText) {
        InfoInputTextField infoInputTextField = new InfoInputTextField();
        InputTextField input = infoInputTextField.getInputTextField();
        input.setPromptText(promptText);

        Label label = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        label.getStyleClass().add("input-label");

        HBox box = new HBox();
        box.getStyleClass().add("input-with-border");
        box.setMaxWidth(243);
        box.getChildren().addAll(infoInputTextField, label);
        return new Tuple3<>(box, infoInputTextField, label);
    }

    public static Tuple3<HBox, TextField, Label> getNonEditableValueCurrencyBox() {
        TextField textField = new InputTextField();
        textField.setPrefWidth(190);
        textField.setAlignment(Pos.CENTER_RIGHT);
        textField.setMouseTransparent(true);
        textField.setEditable(false);
        textField.setFocusTraversable(false);

        Label currency = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        currency.setId("currency-info-label-disabled");

        HBox box = new HBox();
        box.getChildren().addAll(textField, currency);
        return new Tuple3<>(box, textField, currency);
    }

    public static Tuple3<HBox, InfoTextField, Label> getNonEditableValueCurrencyBoxWithInfo() {
        InfoTextField infoTextField = new InfoTextField();
        infoTextField.setIconsLeftAligned();
        TextField textField = infoTextField.getTextField();
        textField.setPrefWidth(190);
        textField.setAlignment(Pos.CENTER_RIGHT);
        textField.setMouseTransparent(true);
        textField.setEditable(false);
        textField.setFocusTraversable(false);

        Label currency = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        currency.setId("currency-info-label-disabled");

        HBox box = new HBox();
        box.getChildren().addAll(infoTextField, currency);
        return new Tuple3<>(box, infoTextField, currency);
    }

    public static Tuple3<HBox, InputTextField, Label> getAmountCurrencyBox(String promptText) {
        InputTextField input = new InputTextField();
        input.setPrefWidth(190);
        input.setAlignment(Pos.CENTER_RIGHT);
        input.setPromptText(promptText);

        Label currency = new AutoTooltipLabel(Res.getBaseCurrencyCode());
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
        Label descriptionLabel = new AutoTooltipLabel(descriptionText);
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

    public static <T> Tuple3<Label, ListView<T>, VBox> addTopLabelListView(GridPane gridPane, int rowIndex, String title) {
        return addTopLabelListView(gridPane, rowIndex, title, 0);
    }

    public static <T> Tuple3<Label, ListView<T>, VBox> addTopLabelListView(GridPane gridPane, int rowIndex, String title, double top) {
        ListView<T> listView = new ListView<>();

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, listView, top);
        return new Tuple3<>(topLabelWithVBox.first, listView, topLabelWithVBox.second);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + FlowPane
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple2<Label, FlowPane> addTopLabelFlowPane(GridPane gridPane, int rowIndex, String title, double top) {
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);
        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, flowPane, top);

        return new Tuple2<>(topLabelWithVBox.first, flowPane);
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Icons
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Text getIconForLabel(GlyphIcons icon, String iconSize, Label label) {
        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            final Text textIcon = MaterialDesignIconFactory.get().createIcon(icon, iconSize);
            textIcon.setOpacity(0.7);
            label.setContentDisplay(ContentDisplay.LEFT);
            label.setGraphic(textIcon);
            return textIcon;
        } else {
            throw new IllegalArgumentException("Not supported icon type");
        }
    }

    public static Text getSmallIconForLabel(GlyphIcons icon, Label label) {
        return getIconForLabel(icon, "0.769em", label);
    }

    public static Text getIcon(GlyphIcons icon) {
        Text textIcon;

        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            textIcon = MaterialDesignIconFactory.get().createIcon(icon, "1.231em");
        } else {
            throw new IllegalArgumentException("Not supported icon type");
        }

        return textIcon;
    }

    public static Label getIcon(AwesomeIcon icon) {
        final Label label = new Label();
        AwesomeDude.setIcon(label, icon);
        return label;
    }

    public static Label getIconForLabel(AwesomeIcon icon, Label label, String fontSize) {
        AwesomeDude.setIcon(label, icon, fontSize);
        return label;
    }

    public static Button getIconButton(GlyphIcons icon) {
        return getIconButton(icon, "highlight");
    }

    public static Button getIconButton(GlyphIcons icon, String styleClass) {
        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            Button textIcon = MaterialDesignIconFactory.get().createIconButton(icon, "", "2em", null, ContentDisplay.CENTER);
            textIcon.setId("icon-button");
            textIcon.getGraphic().getStyleClass().add(styleClass);
            textIcon.setPrefWidth(20);
            textIcon.setPrefHeight(20);
            textIcon.setPadding(new Insets(0));
            return textIcon;
        } else {
            throw new IllegalArgumentException("Not supported icon type");
        }
    }

}
