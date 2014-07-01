package io.bitsquare.gui.util;


import io.bitsquare.gui.components.VSpacer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;

//TODO to be removed
@SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
public class FormBuilder
{

    public static Label addLabel(GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        Label valueLabel = new Label(value);
        gridPane.add(valueLabel, 1, row);
        return valueLabel;
    }


    public static Label addHeaderLabel(GridPane gridPane, String title, int row, int column)
    {
        Label headerLabel = new Label(title);
        headerLabel.setId("form-header-text");
        gridPane.add(headerLabel, column, row);
        return headerLabel;
    }


    public static Label addHeaderLabel(GridPane gridPane, String title, int row)
    {
        return addHeaderLabel(gridPane, title, row, 0);
    }


    public static TextField addTextField(GridPane gridPane, String title, String value, int row)
    {
        return addTextField(gridPane, title, value, row, false, false);
    }


    public static TextField addTextField(GridPane gridPane, String title, String value, int row, boolean editable, boolean selectable)
    {
        gridPane.add(new Label(title), 0, row);
        TextField textField = new TextField(value);
        gridPane.add(textField, 1, row);
        textField.setMouseTransparent(!selectable && !editable);
        textField.setEditable(editable);

        return textField;
    }

    public static void addVSpacer(GridPane gridPane, int row)
    {
        gridPane.add(new VSpacer(10), 0, row);
    }


    public static Button addButton(GridPane gridPane, String title, int row)
    {
        Button button = new Button(title);
        gridPane.add(button, 1, row);
        return button;
    }


}
