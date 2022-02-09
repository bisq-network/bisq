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
import bisq.desktop.components.AutocompleteComboBox;
import bisq.desktop.components.BalanceTextField;
import bisq.desktop.components.BisqTextArea;
import bisq.desktop.components.BisqTextField;
import bisq.desktop.components.BsqAddressTextField;
import bisq.desktop.components.BusyAnimation;
import bisq.desktop.components.ExplorerAddressTextField;
import bisq.desktop.components.ExternalHyperlink;
import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.HyperlinkWithIcon;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InfoTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.PasswordTextField;
import bisq.desktop.components.SimpleMarkdownLabel;
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
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
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
    private static final String MATERIAL_DESIGN_ICONS = "'Material Design Icons'";

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TitledGroupBg
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane, int rowIndex, int rowSpan, String title) {
        return addTitledGroupBg(gridPane, rowIndex, rowSpan, title, 0);
    }

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane,
                                                 int rowIndex,
                                                 int columnIndex,
                                                 int rowSpan,
                                                 String title) {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, rowIndex, rowSpan, title, 0);
        GridPane.setColumnIndex(titledGroupBg, columnIndex);
        return titledGroupBg;
    }

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane,
                                                 int rowIndex,
                                                 int columnIndex,
                                                 int rowSpan,
                                                 String title,
                                                 double top) {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, rowIndex, rowSpan, title, top);
        GridPane.setColumnIndex(titledGroupBg, columnIndex);
        return titledGroupBg;
    }

    public static TitledGroupBg addTitledGroupBg(GridPane gridPane,
                                                 int rowIndex,
                                                 int rowSpan,
                                                 String title,
                                                 double top) {
        TitledGroupBg titledGroupBg = new TitledGroupBg();
        titledGroupBg.setText(title);
        titledGroupBg.prefWidthProperty().bind(gridPane.widthProperty());
        GridPane.setRowIndex(titledGroupBg, rowIndex);
        GridPane.setRowSpan(titledGroupBg, rowSpan);
        GridPane.setMargin(titledGroupBg, new Insets(top + 8, -10, -12, -10));
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
    // Label + Subtext
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, Label, VBox> addLabelWithSubText(GridPane gridPane,
                                                                 int rowIndex,
                                                                 String title,
                                                                 String description) {
        return addLabelWithSubText(gridPane, rowIndex, title, description, 0);
    }

    public static Tuple3<Label, Label, VBox> addLabelWithSubText(GridPane gridPane,
                                                                 int rowIndex,
                                                                 String title,
                                                                 String description,
                                                                 double top) {
        Label label = new AutoTooltipLabel(title);
        Label subText = new AutoTooltipLabel(description);

        VBox vBox = new VBox();
        vBox.getChildren().setAll(label, subText);

        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setMargin(vBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(vBox);

        return new Tuple3<>(label, subText, vBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Simple Markdown Label
    ///////////////////////////////////////////////////////////////////////////////////////////
    public static SimpleMarkdownLabel addSimpleMarkdownLabel(GridPane gridPane, int rowIndex) {
        return addSimpleMarkdownLabel(gridPane, rowIndex, null, 0);
    }

    public static SimpleMarkdownLabel addSimpleMarkdownLabel(GridPane gridPane, int rowIndex, String markdown, double top) {
        SimpleMarkdownLabel label = new SimpleMarkdownLabel(markdown);

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
        return addMultilineLabel(gridPane, rowIndex, text, top, 600);
    }

    public static Label addMultilineLabel(GridPane gridPane, int rowIndex, String text, double top, double maxWidth) {
        Label label = new AutoTooltipLabel(text);
        label.setWrapText(true);
        label.setMaxWidth(maxWidth);
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setHgrow(label, Priority.ALWAYS);
        GridPane.setRowIndex(label, rowIndex);
        GridPane.setMargin(label, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(label);
        return label;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", -15);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              int columnIndex,
                                                                              String title) {
        Tuple3<Label, TextField, VBox> tuple = addTopLabelTextField(gridPane, rowIndex, title, "", -15);
        GridPane.setColumnIndex(tuple.third, columnIndex);
        return tuple;
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              double top) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", top - 15);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              String value) {
        return addTopLabelReadOnlyTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              int columnIndex,
                                                                              String title,
                                                                              String value,
                                                                              double top) {
        Tuple3<Label, TextField, VBox> tuple = addTopLabelTextField(gridPane, rowIndex, title, value, top - 15);
        GridPane.setColumnIndex(tuple.third, columnIndex);
        return tuple;
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              int columnIndex,
                                                                              String title,
                                                                              double top) {
        Tuple3<Label, TextField, VBox> tuple = addTopLabelTextField(gridPane, rowIndex, title, "", top - 15);
        GridPane.setColumnIndex(tuple.third, columnIndex);
        return tuple;
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelReadOnlyTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              String value,
                                                                              double top) {
        return addTopLabelTextField(gridPane, rowIndex, title, value, top - 15);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane, int rowIndex, String title) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", 0);
    }

    public static Tuple3<Label, TextField, VBox> addCompactTopLabelTextField(GridPane gridPane,
                                                                             int rowIndex,
                                                                             String title,
                                                                             String value) {
        return addTopLabelTextField(gridPane, rowIndex, title, value, -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple3<Label, TextField, VBox> addCompactTopLabelTextField(GridPane gridPane,
                                                                             int rowIndex,
                                                                             int colIndex,
                                                                             String title,
                                                                             String value) {
        final Tuple3<Label, TextField, VBox> labelTextFieldVBoxTuple3 = addTopLabelTextField(gridPane, rowIndex, title, value, -Layout.FLOATING_LABEL_DISTANCE);
        GridPane.setColumnIndex(labelTextFieldVBoxTuple3.third, colIndex);
        return labelTextFieldVBoxTuple3;
    }

    public static Tuple3<Label, TextField, VBox> addCompactTopLabelTextField(GridPane gridPane,
                                                                             int rowIndex,
                                                                             String title,
                                                                             String value,
                                                                             double top) {
        return addTopLabelTextField(gridPane, rowIndex, title, value, top - Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane,
                                                                      int rowIndex,
                                                                      String title,
                                                                      String value) {
        return addTopLabelTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane,
                                                                      int rowIndex,
                                                                      String title,
                                                                      double top) {
        return addTopLabelTextField(gridPane, rowIndex, title, "", top);
    }

    public static Tuple3<Label, TextField, VBox> addTopLabelTextField(GridPane gridPane,
                                                                      int rowIndex,
                                                                      String title,
                                                                      String value,
                                                                      double top) {
        TextField textField = new BisqTextField(value);
        textField.setEditable(false);
        textField.setFocusTraversable(false);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textField, top);

        // TODO not 100% sure if that is a good idea....
        //topLabelWithVBox.first.getStyleClass().add("jfx-text-field-top-label");

        return new Tuple3<>(topLabelWithVBox.first, textField, topLabelWithVBox.second);
    }

    public static Tuple2<TextField, Button> addTextFieldWithEditButton(GridPane gridPane, int rowIndex, String title) {
        TextField textField = new BisqTextField();
        textField.setPromptText(title);
        textField.setEditable(false);
        textField.setFocusTraversable(false);
        textField.setPrefWidth(Layout.INITIAL_WINDOW_WIDTH);

        Button button = new AutoTooltipButton("...");
        button.setStyle("-fx-min-width: 26; -fx-pref-height: 26; -fx-padding: 0 0 10 0; -fx-background-color: -fx-background;");
        button.managedProperty().bind(button.visibleProperty());

        HBox hbox = new HBox(textField, button);
        hbox.setAlignment(Pos.CENTER_LEFT);
        hbox.setSpacing(8);

        VBox vbox = getTopLabelVBox(0);
        vbox.getChildren().addAll(getTopLabel(title), hbox);

        gridPane.getChildren().add(vbox);
        GridPane.setRowIndex(vbox, rowIndex);
        GridPane.setMargin(vbox, new Insets(Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));

        return new Tuple2<>(textField, button);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Confirmation Fields
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, Label> addConfirmationLabelLabel(GridPane gridPane,
                                                                 int rowIndex,
                                                                 String title1,
                                                                 String title2) {
        return addConfirmationLabelLabel(gridPane, rowIndex, title1, title2, 0);
    }

    public static Tuple2<Label, Label> addConfirmationLabelLabel(GridPane gridPane,
                                                                 int rowIndex,
                                                                 String title1,
                                                                 String title2,
                                                                 double top) {
        Label label1 = addLabel(gridPane, rowIndex, title1);
        label1.getStyleClass().add("confirmation-label");
        Label label2 = addLabel(gridPane, rowIndex, title2);
        label2.getStyleClass().add("confirmation-value");
        GridPane.setColumnIndex(label2, 1);
        GridPane.setMargin(label1, new Insets(top, 0, 0, 0));
        GridPane.setHalignment(label1, HPos.LEFT);
        GridPane.setMargin(label2, new Insets(top, 0, 0, 0));

        return new Tuple2<>(label1, label2);
    }

    public static Tuple2<Label, TextField> addConfirmationLabelTextField(GridPane gridPane,
                                                                         int rowIndex,
                                                                         String title1,
                                                                         String title2) {
        return addConfirmationLabelTextField(gridPane, rowIndex, title1, title2, 0);
    }

    public static Tuple2<Label, TextField> addConfirmationLabelTextField(GridPane gridPane,
                                                                         int rowIndex,
                                                                         String title1,
                                                                         String title2,
                                                                         double top) {
        Label label1 = addLabel(gridPane, rowIndex, title1);
        label1.getStyleClass().add("confirmation-label");
        TextField label2 = new BisqTextField(title2);
        gridPane.getChildren().add(label2);
        label2.getStyleClass().add("confirmation-text-field-as-label");
        label2.setEditable(false);
        label2.setFocusTraversable(false);
        GridPane.setRowIndex(label2, rowIndex);
        GridPane.setColumnIndex(label2, 1);
        GridPane.setMargin(label1, new Insets(top, 0, 0, 0));
        GridPane.setHalignment(label1, HPos.LEFT);
        GridPane.setMargin(label2, new Insets(top, 0, 0, 0));
        return new Tuple2<>(label1, label2);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addConfirmationLabelLabelWithCopyIcon(GridPane gridPane,
                                                                                             int rowIndex,
                                                                                             String title1,
                                                                                             String title2) {
        Label label1 = addLabel(gridPane, rowIndex, title1);
        label1.getStyleClass().add("confirmation-label");
        TextFieldWithCopyIcon label2 = new TextFieldWithCopyIcon("confirmation-value");
        label2.setText(title2);
        GridPane.setRowIndex(label2, rowIndex);
        gridPane.getChildren().add(label2);
        GridPane.setColumnIndex(label2, 1);
        GridPane.setHalignment(label1, HPos.LEFT);
        return new Tuple2<>(label1, label2);
    }

    public static Tuple2<Label, TextArea> addConfirmationLabelTextArea(GridPane gridPane,
                                                                       int rowIndex,
                                                                       String title1,
                                                                       String title2,
                                                                       double top) {
        Label label = addLabel(gridPane, rowIndex, title1);
        label.getStyleClass().add("confirmation-label");

        TextArea textArea = addTextArea(gridPane, rowIndex, title2);
        ((JFXTextArea) textArea).setLabelFloat(false);

        GridPane.setColumnIndex(textArea, 1);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setMargin(textArea, new Insets(top, 0, 0, 0));

        return new Tuple2<>(label, textArea);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextFieldWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextFieldWithIcon> addTopLabelTextFieldWithIcon(GridPane gridPane,
                                                                                int rowIndex,
                                                                                String title,
                                                                                double top) {
        return addTopLabelTextFieldWithIcon(gridPane, rowIndex, 0, title, top);
    }

    public static Tuple2<Label, TextFieldWithIcon> addTopLabelTextFieldWithIcon(GridPane gridPane,
                                                                                int rowIndex,
                                                                                int columnIndex,
                                                                                String title,
                                                                                double top) {

        TextFieldWithIcon textFieldWithIcon = new TextFieldWithIcon();
        textFieldWithIcon.setFocusTraversable(false);

        return new Tuple2<>(addTopLabelWithVBox(gridPane, rowIndex, columnIndex, title, textFieldWithIcon, top).first, textFieldWithIcon);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    //  HyperlinkWithIcon
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static HyperlinkWithIcon addHyperlinkWithIcon(GridPane gridPane, int rowIndex, String title, String url) {
        return addHyperlinkWithIcon(gridPane, rowIndex, title, url, 0);
    }

    public static HyperlinkWithIcon addHyperlinkWithIcon(GridPane gridPane,
                                                         int rowIndex,
                                                         String title,
                                                         String url,
                                                         double top) {
        HyperlinkWithIcon hyperlinkWithIcon = new ExternalHyperlink(title);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(url));
        GridPane.setRowIndex(hyperlinkWithIcon, rowIndex);
        GridPane.setColumnIndex(hyperlinkWithIcon, 0);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(top, 0, 0, 0));
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

        HyperlinkWithIcon hyperlinkWithIcon = new ExternalHyperlink(title);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(url));
        GridPane.setRowIndex(hyperlinkWithIcon, rowIndex);
        GridPane.setMargin(hyperlinkWithIcon, new Insets(top, 0, 0, -4));
        gridPane.getChildren().add(hyperlinkWithIcon);
        return new Tuple2<>(label, hyperlinkWithIcon);
    }

    public static Tuple3<Label, HyperlinkWithIcon, VBox> addTopLabelHyperlinkWithIcon(GridPane gridPane,
                                                                                      int rowIndex,
                                                                                      int columnIndex,
                                                                                      String title,
                                                                                      String value,
                                                                                      String url,
                                                                                      double top) {
        Tuple3<Label, HyperlinkWithIcon, VBox> tuple = addTopLabelHyperlinkWithIcon(gridPane,
                rowIndex,
                title,
                value,
                url,
                top);
        GridPane.setColumnIndex(tuple.third, columnIndex);
        return tuple;
    }

    public static Tuple3<Label, HyperlinkWithIcon, VBox> addTopLabelHyperlinkWithIcon(GridPane gridPane,
                                                                                      int rowIndex,
                                                                                      String title,
                                                                                      String value,
                                                                                      String url,
                                                                                      double top) {
        HyperlinkWithIcon hyperlinkWithIcon = new ExternalHyperlink(value);
        hyperlinkWithIcon.setOnAction(e -> GUIUtil.openWebPage(url));
        hyperlinkWithIcon.getStyleClass().add("hyperlink-with-icon");
        GridPane.setRowIndex(hyperlinkWithIcon, rowIndex);
        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, hyperlinkWithIcon, top - 15);
        return new Tuple3<>(topLabelWithVBox.first, hyperlinkWithIcon, topLabelWithVBox.second);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // TextArea
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static TextArea addTextArea(GridPane gridPane, int rowIndex, String prompt) {
        return addTextArea(gridPane, rowIndex, prompt, 0);
    }

    public static TextArea addTextArea(GridPane gridPane, int rowIndex, String prompt, double top) {

        JFXTextArea textArea = new BisqTextArea();
        textArea.setPromptText(prompt);
        textArea.setLabelFloat(true);
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

    public static Tuple2<Label, TextArea> addCompactTopLabelTextArea(GridPane gridPane,
                                                                     int rowIndex,
                                                                     String title,
                                                                     String prompt) {
        return addTopLabelTextArea(gridPane, rowIndex, title, prompt, -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextArea> addCompactTopLabelTextArea(GridPane gridPane,
                                                                     int rowIndex,
                                                                     int colIndex,
                                                                     String title,
                                                                     String prompt) {
        return addTopLabelTextArea(gridPane, rowIndex, colIndex, title, prompt, -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane,
                                                              int rowIndex,
                                                              String title,
                                                              String prompt) {
        return addTopLabelTextArea(gridPane, rowIndex, title, prompt, 0);
    }

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane,
                                                              int rowIndex,
                                                              int colIndex,
                                                              String title,
                                                              String prompt) {
        return addTopLabelTextArea(gridPane, rowIndex, colIndex, title, prompt, 0);
    }

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane,
                                                              int rowIndex,
                                                              String title,
                                                              String prompt,
                                                              double top) {

        return addTopLabelTextArea(gridPane, rowIndex, 0, title, prompt, top);
    }

    public static Tuple2<Label, TextArea> addTopLabelTextArea(GridPane gridPane, int rowIndex, int colIndex,
                                                              String title, String prompt, double top) {

        TextArea textArea = new BisqTextArea();
        textArea.setPromptText(prompt);
        textArea.setWrapText(true);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textArea, top);
        GridPane.setColumnIndex(topLabelWithVBox.second, colIndex);

        return new Tuple2<>(topLabelWithVBox.first, textArea);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + DatePicker
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, DatePicker> addTopLabelDatePicker(GridPane gridPane,
                                                                  int rowIndex,
                                                                  String title,
                                                                  double top) {
        return addTopLabelDatePicker(gridPane, rowIndex, 0, title, top);
    }

    public static Tuple2<Label, DatePicker> addTopLabelDatePicker(GridPane gridPane,
                                                                  int rowIndex,
                                                                  int columnIndex,
                                                                  String title,
                                                                  double top) {
        DatePicker datePicker = new JFXDatePicker();
        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, columnIndex, title, datePicker, top);
        return new Tuple2<>(topLabelWithVBox.first, datePicker);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // 2 DatePickers
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<DatePicker, DatePicker> add2TopLabelDatePicker(GridPane gridPane,
                                                                        int rowIndex,
                                                                        int columnIndex,
                                                                        String title1,
                                                                        String title2,
                                                                        double top) {
        DatePicker datePicker1 = new JFXDatePicker();
        Tuple2<Label, VBox> topLabelWithVBox1 = getTopLabelWithVBox(title1, datePicker1);
        VBox vBox1 = topLabelWithVBox1.second;

        DatePicker datePicker2 = new JFXDatePicker();
        Tuple2<Label, VBox> topLabelWithVBox2 = getTopLabelWithVBox(title2, datePicker2);
        VBox vBox2 = topLabelWithVBox2.second;

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(spacer, vBox1, vBox2);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setColumnIndex(hBox, columnIndex);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);
        return new Tuple2<>(datePicker1, datePicker2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TxIdTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    @SuppressWarnings("UnusedReturnValue")
    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane,
                                                                     int rowIndex,
                                                                     String title,
                                                                     String value) {
        return addLabelTxIdTextField(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane,
                                                                     int rowIndex,
                                                                     String title,
                                                                     String value,
                                                                     double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);
        label.getStyleClass().add("confirmation-label");
        GridPane.setHalignment(label, HPos.LEFT);

        TxIdTextField txTextField = new TxIdTextField();
        txTextField.setup(value);
        GridPane.setRowIndex(txTextField, rowIndex);
        GridPane.setColumnIndex(txTextField, 1);
        gridPane.getChildren().add(txTextField);

        return new Tuple2<>(label, txTextField);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + ExplorerAddressTextField
    ///////////////////////////////////////////////////////////////////////////////////////////
    public static void addLabelExplorerAddressTextField(GridPane gridPane,
                                                        int rowIndex,
                                                        String title,
                                                        String address) {
        Label label = addLabel(gridPane, rowIndex, title, 0);
        label.getStyleClass().add("confirmation-label");
        GridPane.setHalignment(label, HPos.LEFT);

        ExplorerAddressTextField addressTextField = new ExplorerAddressTextField();
        addressTextField.setup(address);
        GridPane.setRowIndex(addressTextField, rowIndex);
        GridPane.setColumnIndex(addressTextField, 1);
        gridPane.getChildren().add(addressTextField);
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
    // Label  + InputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, InputTextField> addTopLabelInputTextField(GridPane gridPane,
                                                                          int rowIndex,
                                                                          String title) {
        return addTopLabelInputTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, InputTextField> addTopLabelInputTextField(GridPane gridPane,
                                                                          int rowIndex,
                                                                          String title,
                                                                          double top) {

        final Tuple3<Label, InputTextField, VBox> topLabelWithVBox = addTopLabelInputTextFieldWithVBox(gridPane, rowIndex, title, top);

        return new Tuple2<>(topLabelWithVBox.first, topLabelWithVBox.second);
    }

    public static Tuple3<Label, InputTextField, VBox> addTopLabelInputTextFieldWithVBox(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        String title,
                                                                                        double top) {

        InputTextField inputTextField = new InputTextField();

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, inputTextField, top);

        return new Tuple3<>(topLabelWithVBox.first, inputTextField, topLabelWithVBox.second);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InfoInputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, InfoInputTextField> addTopLabelInfoInputTextField(GridPane gridPane,
                                                                                  int rowIndex,
                                                                                  String title) {
        return addTopLabelInfoInputTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple2<Label, InfoInputTextField> addTopLabelInfoInputTextField(GridPane gridPane,
                                                                                  int rowIndex,
                                                                                  String title,
                                                                                  double top) {

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

    public static Tuple3<Label, InputTextField, ToggleButton> addTopLabelInputTextFieldSlideToggleButton(GridPane gridPane,
                                                                                                         int rowIndex,
                                                                                                         String title,
                                                                                                         String toggleButtonTitle) {

        InputTextField inputTextField = new InputTextField();
        ToggleButton toggleButton = new JFXToggleButton();
        toggleButton.setText(toggleButtonTitle);
        VBox.setMargin(toggleButton, new Insets(4, 0, 0, 0));

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, inputTextField, 0);

        topLabelWithVBox.second.getChildren().add(toggleButton);

        return new Tuple3<>(topLabelWithVBox.first, inputTextField, toggleButton);
    }


    public static Tuple3<Label, InputTextField, ToggleButton> addTopLabelInputTextFieldSlideToggleButtonRight(GridPane gridPane,
                                                                                                              int rowIndex,
                                                                                                              String title,
                                                                                                              String toggleButtonTitle) {

        InputTextField inputTextField = new InputTextField();
        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, inputTextField, 0);
        ToggleButton toggleButton = new JFXToggleButton();
        toggleButton.setText(toggleButtonTitle);
        HBox hBox = new HBox();
        hBox.getChildren().addAll(topLabelWithVBox.second, toggleButton);
        HBox.setMargin(toggleButton, new Insets(9, 0, 0, 0));
        gridPane.add(hBox, 0, rowIndex);
        GridPane.setMargin(hBox, new Insets(Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        return new Tuple3<>(topLabelWithVBox.first, inputTextField, toggleButton);
    }



    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, InputTextField, Button> addTopLabelInputTextFieldButton(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        String title,
                                                                                        String buttonTitle) {
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

    public static Tuple3<Label, TextField, Button> addTopLabelTextFieldButton(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              String buttonTitle) {
        return addTopLabelTextFieldButton(gridPane, rowIndex, title, buttonTitle, 0);
    }

    public static Tuple3<Label, TextField, Button> addTopLabelTextFieldButton(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              String buttonTitle,
                                                                              double top) {

        TextField textField = new BisqTextField();
        textField.setEditable(false);
        textField.setMouseTransparent(true);
        textField.setFocusTraversable(false);
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField, button);
        HBox.setHgrow(textField, Priority.ALWAYS);

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, top);

        return new Tuple3<>(labelVBoxTuple2.first, textField, button);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + InputTextField + Label  + InputTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<InputTextField, InputTextField> addInputTextFieldInputTextField(GridPane gridPane,
                                                                                         int rowIndex,
                                                                                         String title1,
                                                                                         String title2) {

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

    public static Tuple4<Label, TextField, Label, TextField> addCompactTopLabelTextFieldTopLabelTextField(GridPane gridPane,
                                                                                                          int rowIndex,
                                                                                                          String title1,
                                                                                                          String title2) {
        TextField textField1 = new BisqTextField();
        textField1.setEditable(false);
        textField1.setMouseTransparent(true);
        textField1.setFocusTraversable(false);

        final Tuple2<Label, VBox> topLabelWithVBox1 = getTopLabelWithVBox(title1, textField1);

        TextField textField2 = new BisqTextField();
        textField2.setEditable(false);
        textField2.setMouseTransparent(true);
        textField2.setFocusTraversable(false);

        final Tuple2<Label, VBox> topLabelWithVBox2 = getTopLabelWithVBox(title2, textField2);

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(topLabelWithVBox1.second, topLabelWithVBox2.second);
        GridPane.setRowIndex(hBox, rowIndex);
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(topLabelWithVBox1.first, textField1, topLabelWithVBox2.first, textField2);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Button + CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Button, CheckBox> addButtonCheckBox(GridPane gridPane,
                                                             int rowIndex,
                                                             String buttonTitle,
                                                             String checkBoxTitle) {
        return addButtonCheckBox(gridPane, rowIndex, buttonTitle, checkBoxTitle, 0);
    }

    public static Tuple2<Button, CheckBox> addButtonCheckBox(GridPane gridPane,
                                                             int rowIndex,
                                                             String buttonTitle,
                                                             String checkBoxTitle,
                                                             double top) {
        final Tuple3<Button, CheckBox, HBox> tuple = addButtonCheckBoxWithBox(gridPane, rowIndex, buttonTitle, checkBoxTitle, top);
        return new Tuple2<>(tuple.first, tuple.second);
    }

    public static Tuple3<Button, CheckBox, HBox> addButtonCheckBoxWithBox(GridPane gridPane,
                                                                          int rowIndex,
                                                                          String buttonTitle,
                                                                          String checkBoxTitle,
                                                                          double top) {
        Button button = new AutoTooltipButton(buttonTitle);
        CheckBox checkBox = new AutoTooltipCheckBox(checkBoxTitle);

        HBox hBox = new HBox(20);
        hBox.setAlignment(Pos.CENTER_LEFT);
        hBox.getChildren().addAll(button, checkBox);
        GridPane.setRowIndex(hBox, rowIndex);
        hBox.setPadding(new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(button, checkBox, hBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // CheckBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static CheckBox addCheckBox(GridPane gridPane, int rowIndex, String checkBoxTitle) {
        return addCheckBox(gridPane, rowIndex, checkBoxTitle, 0);
    }

    public static CheckBox addCheckBox(GridPane gridPane, int rowIndex, String checkBoxTitle, double top) {
        return addCheckBox(gridPane, rowIndex, 0, checkBoxTitle, top);
    }

    public static CheckBox addCheckBox(GridPane gridPane,
                                       int rowIndex,
                                       int colIndex,
                                       String checkBoxTitle,
                                       double top) {
        CheckBox checkBox = new AutoTooltipCheckBox(checkBoxTitle);
        GridPane.setMargin(checkBox, new Insets(top, 0, 0, 0));
        GridPane.setRowIndex(checkBox, rowIndex);
        GridPane.setColumnIndex(checkBox, colIndex);
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
                                                                                            double top) {
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
    // Label + TextField + RadioButton + RadioButton
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple4<Label, TextField, RadioButton, RadioButton> addTopLabelTextFieldRadioButtonRadioButton(GridPane gridPane,
                                                                                                                int rowIndex,
                                                                                                                ToggleGroup toggleGroup,
                                                                                                                String title,
                                                                                                                String textFieldTitle,
                                                                                                                String radioButtonTitle1,
                                                                                                                String radioButtonTitle2,
                                                                                                                double top) {
        TextField textField = new BisqTextField();
        textField.setPromptText(textFieldTitle);

        RadioButton radioButton1 = new AutoTooltipRadioButton(radioButtonTitle1);
        radioButton1.setToggleGroup(toggleGroup);
        radioButton1.setPadding(new Insets(6, 0, 0, 0));

        RadioButton radioButton2 = new AutoTooltipRadioButton(radioButtonTitle2);
        radioButton2.setToggleGroup(toggleGroup);
        radioButton2.setPadding(new Insets(6, 0, 0, 0));

        HBox hBox = new HBox();
        hBox.setSpacing(10);
        hBox.getChildren().addAll(textField, radioButton1, radioButton2);

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, top);

        return new Tuple4<>(labelVBoxTuple2.first, textField, radioButton1, radioButton2);
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

    public static <T> Tuple2<Label, ComboBox<T>> addTopLabelComboBox(GridPane gridPane,
                                                                     int rowIndex,
                                                                     String title,
                                                                     String prompt,
                                                                     int top) {
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

    public static <T> Tuple3<VBox, Label, AutocompleteComboBox<T>> addTopLabelAutocompleteComboBox(String title) {
        return addTopLabelAutocompleteComboBox(title, 0);
    }

    public static <T> Tuple3<VBox, Label, AutocompleteComboBox<T>> addTopLabelAutocompleteComboBox(String title,
                                                                                                   int top) {
        Label label = getTopLabel(title);
        VBox vBox = getTopLabelVBox(top);

        final AutocompleteComboBox<T> comboBox = new AutocompleteComboBox<>();

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

    public static Tuple2<Label, VBox> addTopLabelWithVBox(GridPane gridPane,
                                                          int rowIndex,
                                                          String title,
                                                          Node node,
                                                          double top) {
        return addTopLabelWithVBox(gridPane, rowIndex, 0, title, node, top);
    }

    @NotNull
    public static Tuple2<Label, VBox> addTopLabelWithVBox(GridPane gridPane,
                                                          int rowIndex,
                                                          int columnIndex,
                                                          String title,
                                                          Node node,
                                                          double top) {
        final Tuple2<Label, VBox> topLabelWithVBox = getTopLabelWithVBox(title, node);
        VBox vBox = topLabelWithVBox.second;

        GridPane.setRowIndex(vBox, rowIndex);
        GridPane.setColumnIndex(vBox, columnIndex);
        GridPane.setMargin(vBox, new Insets(top + Layout.FLOATING_LABEL_DISTANCE, 0, 0, 0));
        gridPane.getChildren().add(vBox);

        return new Tuple2<>(topLabelWithVBox.first, vBox);
    }

    @NotNull
    public static Tuple2<Label, VBox> getTopLabelWithVBox(String title, Node node) {
        Label label = getTopLabel(title);
        VBox vBox = getTopLabelVBox(0);
        vBox.getChildren().addAll(label, node);

        return new Tuple2<>(label, vBox);
    }

    public static Tuple3<Label, TextField, HBox> addTopLabelTextFieldWithHbox(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String titleTextfield,
                                                                              double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        TextField textField = new BisqTextField();

        final VBox topLabelVBox = getTopLabelVBox(5);
        final Label topLabel = getTopLabel(titleTextfield);
        topLabelVBox.getChildren().addAll(topLabel, textField);

        hBox.getChildren().addAll(topLabelVBox);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple3<>(topLabel, textField, hBox);
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
    // Label  + AutocompleteComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple2<Label, ComboBox<T>> addLabelAutocompleteComboBox(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              double top) {
        AutocompleteComboBox<T> comboBox = new AutocompleteComboBox<>();
        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, title, comboBox, top);
        return new Tuple2<>(labelVBoxTuple2.first, comboBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + TextField + AutocompleteComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple4<Label, TextField, Label, ComboBox<T>> addTopLabelTextFieldAutocompleteComboBox(
            GridPane gridPane,
            int rowIndex,
            String titleTextfield,
            String titleCombobox
    ) {
        return addTopLabelTextFieldAutocompleteComboBox(gridPane, rowIndex, titleTextfield, titleCombobox, 0);
    }

    public static <T> Tuple4<Label, TextField, Label, ComboBox<T>> addTopLabelTextFieldAutocompleteComboBox(
            GridPane gridPane,
            int rowIndex,
            String titleTextfield,
            String titleCombobox,
            double top
    ) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        final VBox topLabelVBox1 = getTopLabelVBox(5);
        final Label topLabel1 = getTopLabel(titleTextfield);
        final TextField textField = new BisqTextField();
        topLabelVBox1.getChildren().addAll(topLabel1, textField);

        final VBox topLabelVBox2 = getTopLabelVBox(5);
        final Label topLabel2 = getTopLabel(titleCombobox);
        AutocompleteComboBox<T> comboBox = new AutocompleteComboBox<>();
        comboBox.setPromptText(titleCombobox);
        comboBox.setLabelFloat(true);
        topLabelVBox2.getChildren().addAll(topLabel2, comboBox);

        hBox.getChildren().addAll(topLabelVBox1, topLabelVBox2);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(topLabel1, textField, topLabel2, comboBox);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + ComboBox + ComboBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T, R> Tuple3<Label, ComboBox<R>, ComboBox<T>> addTopLabelComboBoxComboBox(GridPane gridPane,
                                                                                             int rowIndex,
                                                                                             String title) {
        return addTopLabelComboBoxComboBox(gridPane, rowIndex, title, 0);
    }

    public static <T, R> Tuple3<Label, ComboBox<T>, ComboBox<R>> addTopLabelComboBoxComboBox(GridPane gridPane,
                                                                                             int rowIndex,
                                                                                             String title,
                                                                                             double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        ComboBox<T> comboBox1 = new JFXComboBox<>();
        ComboBox<R> comboBox2 = new JFXComboBox<>();
        hBox.getChildren().addAll(comboBox1, comboBox2);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, hBox, top);

        return new Tuple3<>(topLabelWithVBox.first, comboBox1, comboBox2);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + ComboBox + TextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple4<ComboBox<T>, Label, TextField, HBox> addComboBoxTopLabelTextField(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               String titleCombobox,
                                                                                               String titleTextfield) {
        return addComboBoxTopLabelTextField(gridPane, rowIndex, titleCombobox, titleTextfield, 0);
    }

    public static <T> Tuple4<ComboBox<T>, Label, TextField, HBox> addComboBoxTopLabelTextField(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               String titleCombobox,
                                                                                               String titleTextfield,
                                                                                               double top) {
        HBox hBox = new HBox();
        hBox.setSpacing(10);

        JFXComboBox<T> comboBox = new JFXComboBox<>();
        comboBox.setPromptText(titleCombobox);
        comboBox.setLabelFloat(true);

        TextField textField = new BisqTextField();

        final VBox topLabelVBox = getTopLabelVBox(5);
        final Label topLabel = getTopLabel(titleTextfield);
        topLabelVBox.getChildren().addAll(topLabel, textField);

        hBox.getChildren().addAll(comboBox, topLabelVBox);

        GridPane.setRowIndex(hBox, rowIndex);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(comboBox, topLabel, textField, hBox);
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

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane,
                                                                     int rowIndex,
                                                                     int columnIndex,
                                                                     String title) {
        return addLabelTxIdTextField(gridPane, rowIndex, columnIndex, title, 0);
    }

    public static Tuple2<Label, TxIdTextField> addLabelTxIdTextField(GridPane gridPane,
                                                                     int rowIndex,
                                                                     int columnIndex,
                                                                     String title,
                                                                     double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);

        TxIdTextField txIdTextField = new TxIdTextField();
        GridPane.setRowIndex(txIdTextField, rowIndex);
        GridPane.setColumnIndex(txIdTextField, columnIndex);
        GridPane.setMargin(txIdTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(txIdTextField);

        return new Tuple2<>(label, txIdTextField);
    }


    public static Tuple3<Label, TxIdTextField, VBox> addTopLabelTxIdTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String title,
                                                                              double top) {
        TxIdTextField textField = new TxIdTextField();
        textField.setFocusTraversable(false);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textField, top);

        // TODO not 100% sure if that is a good idea....
        //topLabelWithVBox.first.getStyleClass().add("jfx-text-field-top-label");

        return new Tuple3<>(topLabelWithVBox.first, textField, topLabelWithVBox.second);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + TextFieldWithCopyIcon
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, TextFieldWithCopyIcon> addCompactTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               String title,
                                                                                               String value) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addCompactTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               int colIndex,
                                                                                               String title,
                                                                                               String value,
                                                                                               double top) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, colIndex, title, value, top - Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addCompactTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               int colIndex,
                                                                                               String title) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, colIndex, title, "", -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addCompactTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               int colIndex,
                                                                                               String title,
                                                                                               String value) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, colIndex, title, value, -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addCompactTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                               int rowIndex,
                                                                                               int colIndex,
                                                                                               String title,
                                                                                               String value,
                                                                                               boolean onlyCopyTextAfterDelimiter) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, colIndex, title, value, -Layout.FLOATING_LABEL_DISTANCE, onlyCopyTextAfterDelimiter);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        String title,
                                                                                        String value) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        String title,
                                                                                        String value,
                                                                                        double top) {
        return addTopLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, top, null);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        String title,
                                                                                        String value,
                                                                                        double top,
                                                                                        String styleClass) {
        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon(styleClass);
        textFieldWithCopyIcon.setText(value);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textFieldWithCopyIcon, top);

        return new Tuple2<>(topLabelWithVBox.first, textFieldWithCopyIcon);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        int colIndex,
                                                                                        String title,
                                                                                        String value,
                                                                                        double top,
                                                                                        boolean onlyCopyTextAfterDelimiter) {

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon();
        textFieldWithCopyIcon.setText(value);
        textFieldWithCopyIcon.setCopyTextAfterDelimiter(true);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textFieldWithCopyIcon, top);
        topLabelWithVBox.second.setAlignment(Pos.TOP_LEFT);
        GridPane.setColumnIndex(topLabelWithVBox.second, colIndex);

        return new Tuple2<>(topLabelWithVBox.first, textFieldWithCopyIcon);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addTopLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                        int rowIndex,
                                                                                        int colIndex,
                                                                                        String title,
                                                                                        String value,
                                                                                        double top) {

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon();
        textFieldWithCopyIcon.setText(value);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, textFieldWithCopyIcon, top);
        topLabelWithVBox.second.setAlignment(Pos.TOP_LEFT);
        GridPane.setColumnIndex(topLabelWithVBox.second, colIndex);

        return new Tuple2<>(topLabelWithVBox.first, textFieldWithCopyIcon);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addConfirmationLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                                 int rowIndex,
                                                                                                 String title,
                                                                                                 String value) {
        return addConfirmationLabelTextFieldWithCopyIcon(gridPane, rowIndex, title, value, 0);
    }

    public static Tuple2<Label, TextFieldWithCopyIcon> addConfirmationLabelTextFieldWithCopyIcon(GridPane gridPane,
                                                                                                 int rowIndex,
                                                                                                 String title,
                                                                                                 String value,
                                                                                                 double top) {
        Label label = addLabel(gridPane, rowIndex, title, top);
        label.getStyleClass().add("confirmation-label");
        GridPane.setHalignment(label, HPos.LEFT);

        TextFieldWithCopyIcon textFieldWithCopyIcon = new TextFieldWithCopyIcon("confirmation-text-field-as-label");
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

    public static Tuple3<Label, InfoTextField, VBox> addCompactTopLabelInfoTextField(GridPane gridPane,
                                                                                     int rowIndex,
                                                                                     String labelText,
                                                                                     String fieldText) {
        return addTopLabelInfoTextField(gridPane, rowIndex, labelText, fieldText,
                -Layout.FLOATING_LABEL_DISTANCE);
    }

    public static Tuple3<Label, InfoTextField, VBox> addTopLabelInfoTextField(GridPane gridPane,
                                                                              int rowIndex,
                                                                              String labelText,
                                                                              String fieldText,
                                                                              double top) {
        InfoTextField infoTextField = new InfoTextField();
        infoTextField.setText(fieldText);

        final Tuple2<Label, VBox> labelVBoxTuple2 = addTopLabelWithVBox(gridPane, rowIndex, labelText, infoTextField, top);

        return new Tuple3<>(labelVBoxTuple2.first, infoTextField, labelVBoxTuple2.second);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + BsqAddressTextField
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, BsqAddressTextField, VBox> addLabelBsqAddressTextField(GridPane gridPane,
                                                                                       int rowIndex,
                                                                                       String title) {
        return addLabelBsqAddressTextField(gridPane, rowIndex, title, 0);
    }

    public static Tuple3<Label, BsqAddressTextField, VBox> addLabelBsqAddressTextField(GridPane gridPane,
                                                                                       int rowIndex,
                                                                                       String title,
                                                                                       double top) {
        BsqAddressTextField addressTextField = new BsqAddressTextField();
        addressTextField.setFocusTraversable(false);

        Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, addressTextField, top - 15);

        return new Tuple3<>(topLabelWithVBox.first, addressTextField, topLabelWithVBox.second);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + BalanceTextField
    ///////////////////////////////////////////////////////////////////////////////////////////


    public static BalanceTextField addBalanceTextField(GridPane gridPane, int rowIndex, String title) {
        return addBalanceTextField(gridPane, rowIndex, title, 20);
    }

    public static BalanceTextField addBalanceTextField(GridPane gridPane, int rowIndex, String title, double top) {
        BalanceTextField balanceTextField = new BalanceTextField(title);
        GridPane.setRowIndex(balanceTextField, rowIndex);
        GridPane.setColumnIndex(balanceTextField, 0);
        GridPane.setMargin(balanceTextField, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(balanceTextField);

        return balanceTextField;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, Button> addTopLabelButton(GridPane gridPane,
                                                          int rowIndex,
                                                          String labelText,
                                                          String buttonTitle) {
        return addTopLabelButton(gridPane, rowIndex, labelText, buttonTitle, 0);
    }

    public static Tuple2<Label, Button> addTopLabelButton(GridPane gridPane,
                                                          int rowIndex,
                                                          String labelText,
                                                          String buttonTitle,
                                                          double top) {
        Button button = new AutoTooltipButton(buttonTitle);
        button.setDefaultButton(true);

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, labelText, button, top);

        return new Tuple2<>(topLabelWithVBox.first, button);
    }

    public static Tuple2<Label, Button> addConfirmationLabelButton(GridPane gridPane,
                                                                   int rowIndex,
                                                                   String labelText,
                                                                   String buttonTitle,
                                                                   double top) {
        Label label = addLabel(gridPane, rowIndex, labelText);
        label.getStyleClass().add("confirmation-label");

        Button button = new AutoTooltipButton(buttonTitle);
        button.getStyleClass().add("confirmation-value");
        button.setDefaultButton(true);

        GridPane.setColumnIndex(button, 1);
        GridPane.setRowIndex(button, rowIndex);
        GridPane.setMargin(label, new Insets(top, 0, 0, 0));
        GridPane.setHalignment(label, HPos.LEFT);
        GridPane.setMargin(button, new Insets(top, 0, 0, 0));

        gridPane.getChildren().add(button);

        return new Tuple2<>(label, button);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label + Button + Button
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<Label, Button, Button> addTopLabel2Buttons(GridPane gridPane,
                                                                    int rowIndex,
                                                                    String labelText,
                                                                    String title1,
                                                                    String title2,
                                                                    double top) {
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

    public static Button addPrimaryActionButtonAFterGroup(GridPane gridPane, int rowIndex, String title) {
        return addPrimaryActionButton(gridPane, rowIndex, title, 15);
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
        final Tuple3<Button, Button, HBox> buttonButtonHBoxTuple3 = add2ButtonsWithBox(gridPane, rowIndex, title1, title2, top, hasPrimaryButton);
        return new Tuple2<>(buttonButtonHBoxTuple3.first, buttonButtonHBoxTuple3.second);
    }

    public static Tuple3<Button, Button, HBox> add2ButtonsWithBox(GridPane gridPane, int rowIndex, String title1,
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
        return new Tuple3<>(button1, button2, hBox);
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

    public static Tuple4<Button, BusyAnimation, Label, HBox> addButtonBusyAnimationLabelAfterGroup(GridPane gridPane,
                                                                                                   int rowIndex,
                                                                                                   int colIndex,
                                                                                                   String buttonTitle) {
        return addButtonBusyAnimationLabel(gridPane, rowIndex, colIndex, buttonTitle, 15);
    }

    public static Tuple4<Button, BusyAnimation, Label, HBox> addButtonBusyAnimationLabelAfterGroup(GridPane gridPane,
                                                                                                   int rowIndex,
                                                                                                   String buttonTitle) {
        return addButtonBusyAnimationLabelAfterGroup(gridPane, rowIndex, 0, buttonTitle);
    }

    public static Tuple4<Button, BusyAnimation, Label, HBox> addButtonBusyAnimationLabel(GridPane gridPane,
                                                                                         int rowIndex,
                                                                                         int colIndex,
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
        GridPane.setHalignment(hBox, HPos.LEFT);
        GridPane.setColumnIndex(hBox, colIndex);
        GridPane.setMargin(hBox, new Insets(top, 0, 0, 0));
        gridPane.getChildren().add(hBox);

        return new Tuple4<>(button, busyAnimation, label, hBox);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade: HBox, InputTextField, Label
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple3<HBox, InputTextField, Label> getEditableValueBox(String promptText) {
        InputTextField input = new InputTextField(60);
        input.setPromptText(promptText);

        Label label = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        label.getStyleClass().add("input-label");

        HBox box = new HBox();
        HBox.setHgrow(input, Priority.ALWAYS);
        input.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("input-with-border");
        box.getChildren().addAll(input, label);
        return new Tuple3<>(box, input, label);
    }

    public static Tuple3<HBox, InfoInputTextField, Label> getEditableValueBoxWithInfo(String promptText) {
        InfoInputTextField infoInputTextField = new InfoInputTextField(60);
        InputTextField input = infoInputTextField.getInputTextField();
        input.setPromptText(promptText);

        Label label = new AutoTooltipLabel(Res.getBaseCurrencyCode());
        label.getStyleClass().add("input-label");

        HBox box = new HBox();
        HBox.setHgrow(infoInputTextField, Priority.ALWAYS);
        infoInputTextField.setMaxWidth(Double.MAX_VALUE);
        box.getStyleClass().add("input-with-border");
        box.getChildren().addAll(infoInputTextField, label);
        return new Tuple3<>(box, infoInputTextField, label);
    }

    public static Tuple3<HBox, TextField, Label> getNonEditableValueBox() {
        final Tuple3<HBox, InputTextField, Label> editableValueBox = getEditableValueBox("");
        final TextField textField = editableValueBox.second;

        textField.setDisable(true);

        return new Tuple3<>(editableValueBox.first, editableValueBox.second, editableValueBox.third);
    }

    public static Tuple3<HBox, InfoInputTextField, Label> getNonEditableValueBoxWithInfo() {

        final Tuple3<HBox, InfoInputTextField, Label> editableValueBoxWithInfo = getEditableValueBoxWithInfo("");

        TextField textField = editableValueBoxWithInfo.second.getInputTextField();
        textField.setDisable(true);

        return editableValueBoxWithInfo;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Trade: Label, VBox
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, VBox> getTradeInputBox(Pane amountValueBox, String descriptionText) {
        Label descriptionLabel = new AutoTooltipLabel(descriptionText);
        descriptionLabel.setId("input-description-label");
        descriptionLabel.setPrefWidth(190);

        VBox box = new VBox();
        box.setPadding(new Insets(10, 0, 0, 0));
        box.setSpacing(2);
        box.getChildren().addAll(descriptionLabel, amountValueBox);
        return new Tuple2<>(descriptionLabel, box);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + List
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static <T> Tuple3<Label, ListView<T>, VBox> addTopLabelListView(GridPane gridPane,
                                                                           int rowIndex,
                                                                           String title) {
        return addTopLabelListView(gridPane, rowIndex, title, 0);
    }

    public static <T> Tuple3<Label, ListView<T>, VBox> addTopLabelListView(GridPane gridPane,
                                                                           int rowIndex,
                                                                           String title,
                                                                           double top) {
        ListView<T> listView = new ListView<>();

        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, listView, top);
        return new Tuple3<>(topLabelWithVBox.first, listView, topLabelWithVBox.second);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Label  + FlowPane
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Tuple2<Label, FlowPane> addTopLabelFlowPane(GridPane gridPane,
                                                              int rowIndex,
                                                              String title,
                                                              double top) {
        return addTopLabelFlowPane(gridPane, rowIndex, title, top, 0);
    }

    public static Tuple2<Label, FlowPane> addTopLabelFlowPane(GridPane gridPane,
                                                              int rowIndex,
                                                              String title,
                                                              double top,
                                                              double bottom) {
        FlowPane flowPane = new FlowPane();
        flowPane.setPadding(new Insets(10, 10, 10, 10));
        flowPane.setVgap(10);
        flowPane.setHgap(10);
        final Tuple2<Label, VBox> topLabelWithVBox = addTopLabelWithVBox(gridPane, rowIndex, title, flowPane, top);

        GridPane.setMargin(topLabelWithVBox.second, new Insets(top + Layout.FLOATING_LABEL_DISTANCE,
                0, bottom, 0));

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
                .filter(e -> GridPane.getRowIndex(e) != null && GridPane.getRowIndex(e) >= fromGridRow && GridPane.getRowIndex(e) <= toGridRow)
                .forEach(e -> gridPane.getChildren().remove(e));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Icons
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static Text getIconForLabel(GlyphIcons icon, String iconSize, Label label, String style) {
        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            final Text textIcon = MaterialDesignIconFactory.get().createIcon(icon, iconSize);
            textIcon.setOpacity(0.7);
            if (style != null) {
                textIcon.getStyleClass().add(style);
            }
            label.setContentDisplay(ContentDisplay.LEFT);
            label.setGraphic(textIcon);
            return textIcon;
        } else {
            throw new IllegalArgumentException("Not supported icon type");
        }
    }

    public static Text getIconForLabel(GlyphIcons icon, String iconSize, Label label) {
        return getIconForLabel(icon, iconSize, label, null);
    }

    public static Text getSmallIconForLabel(GlyphIcons icon, Label label, String style) {
        return getIconForLabel(icon, "0.769em", label, style);
    }

    public static Text getSmallIconForLabel(GlyphIcons icon, Label label) {
        return getIconForLabel(icon, "0.769em", label);
    }

    public static Text getRegularIconForLabel(GlyphIcons icon, Label label) {
        return getRegularIconForLabel(icon, label, null);
    }

    public static Text getRegularIconForLabel(GlyphIcons icon, Label label, String style) {
        return getIconForLabel(icon, "1.231em", label, style);
    }

    public static Text getIcon(GlyphIcons icon) {
        return getIcon(icon, "1.231em");
    }

    public static Text getBigIcon(GlyphIcons icon) {
        return getIcon(icon, "2em");
    }

    public static Text getMediumSizeIcon(GlyphIcons icon) {
        return getIcon(icon, "1.5em");
    }

    public static Text getIcon(GlyphIcons icon, String iconSize) {
        Text textIcon;

        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            textIcon = MaterialDesignIconFactory.get().createIcon(icon, iconSize);
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

    public static Label getIcon(AwesomeIcon icon, String fontSize) {
        return getIconForLabel(icon, new Label(), fontSize);
    }

    public static Label getSmallIcon(AwesomeIcon icon) {
        return getIcon(icon, "1em");
    }

    public static Label getIconForLabel(AwesomeIcon icon, Label label, String fontSize) {
        AwesomeDude.setIcon(label, icon, fontSize);
        return label;
    }

    public static Button getIconButton(GlyphIcons icon) {
        return getIconButton(icon, "highlight");
    }

    public static Button getIconButton(GlyphIcons icon, String styleClass) {
        return getIconButton(icon, styleClass, "2em");
    }

    public static Button getRegularIconButton(GlyphIcons icon) {
        return getIconButton(icon, "highlight", "1.6em");
    }

    public static Button getRegularIconButton(GlyphIcons icon, String styleClass) {
        return getIconButton(icon, styleClass, "1.6em");
    }

    public static Button getIconButton(GlyphIcons icon, String styleClass, String iconSize) {
        if (icon.fontFamily().equals(MATERIAL_DESIGN_ICONS)) {
            Button iconButton = MaterialDesignIconFactory.get().createIconButton(icon,
                    "", iconSize, null, ContentDisplay.CENTER);
            iconButton.setId("icon-button");
            iconButton.getGraphic().getStyleClass().add(styleClass);
            iconButton.setPrefWidth(20);
            iconButton.setPrefHeight(20);
            iconButton.setPadding(new Insets(0));
            return iconButton;
        } else {
            throw new IllegalArgumentException("Not supported icon type");
        }
    }

    public static <T> TableView<T> addTableViewWithHeader(GridPane gridPane, int rowIndex, String headerText) {
        return addTableViewWithHeader(gridPane, rowIndex, headerText, 0, null);
    }

    public static <T> TableView<T> addTableViewWithHeader(GridPane gridPane,
                                                          int rowIndex,
                                                          String headerText,
                                                          String groupStyle) {
        return addTableViewWithHeader(gridPane, rowIndex, headerText, 0, groupStyle);
    }

    public static <T> TableView<T> addTableViewWithHeader(GridPane gridPane, int rowIndex, String headerText, int top) {
        return addTableViewWithHeader(gridPane, rowIndex, headerText, top, null);
    }

    public static <T> TableView<T> addTableViewWithHeader(GridPane gridPane,
                                                          int rowIndex,
                                                          String headerText,
                                                          int top,
                                                          String groupStyle) {
        TitledGroupBg titledGroupBg = addTitledGroupBg(gridPane, rowIndex, 1, headerText, top);

        if (groupStyle != null) titledGroupBg.getStyleClass().add(groupStyle);

        TableView<T> tableView = new TableView<>();
        GridPane.setRowIndex(tableView, rowIndex);
        GridPane.setMargin(tableView, new Insets(top + 30, -10, 5, -10));
        gridPane.getChildren().add(tableView);
        tableView.setPlaceholder(new AutoTooltipLabel(Res.get("table.placeholder.noData")));
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        return tableView;
    }
}

