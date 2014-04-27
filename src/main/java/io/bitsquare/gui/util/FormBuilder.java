package io.bitsquare.gui.util;

import io.bitsquare.gui.components.VSpacer;
import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

import java.util.List;

public class FormBuilder
{
    public static Label addLabel(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        Label valueLabel = new Label(value);
        gridPane.add(valueLabel, 1, row);
        return valueLabel;
    }

    public static void addHeaderLabel(GridPane gridPane, String title, int row)
    {
        Label headerLabel = new Label(title);
        headerLabel.setId("form-header-text");
        gridPane.add(headerLabel, 0, row);
    }

    public static TextField addInputField(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        TextField textField = new TextField(value);
        gridPane.add(textField, 1, row);
        return textField;
    }

    public static void addVSpacer(GridPane gridPane, int row)
    {
        gridPane.add(new VSpacer(10), 0, row);
    }

    public static Button addButton(GridPane gridPane, String title, int row)
    {
        Button button = new Button(title);
        gridPane.add(button, 0, row);
        return button;
    }

    public static ComboBox addComboBox(GridPane gridPane, String title, List<?> list, int row)
    {
        gridPane.add(new Label(title), 0, row);
        ComboBox comboBox = new ComboBox(FXCollections.observableArrayList(list));
        gridPane.add(comboBox, 1, row);
        return comboBox;
    }
}
