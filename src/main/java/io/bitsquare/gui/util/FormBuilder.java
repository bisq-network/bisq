package io.bitsquare.gui.util;


import io.bitsquare.gui.components.VSpacer;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import org.jetbrains.annotations.NotNull;

//TODO to be removed
@SuppressWarnings({"SameParameterValue", "UnusedReturnValue"})
public class FormBuilder
{
    @NotNull
    public static Label addLabel(@NotNull GridPane gridPane, String title, String value, int row)
    {
        gridPane.add(new Label(title), 0, row);
        @NotNull Label valueLabel = new Label(value);
        gridPane.add(valueLabel, 1, row);
        return valueLabel;
    }

    @NotNull
    public static Label addHeaderLabel(@NotNull GridPane gridPane, String title, int row, int column)
    {
        @NotNull Label headerLabel = new Label(title);
        headerLabel.setId("form-header-text");
        gridPane.add(headerLabel, column, row);
        return headerLabel;
    }

    @NotNull
    public static Label addHeaderLabel(@NotNull GridPane gridPane, String title, int row)
    {
        return addHeaderLabel(gridPane, title, row, 0);
    }

    @NotNull
    public static TextField addTextField(@NotNull GridPane gridPane, String title, String value, int row)
    {
        return addTextField(gridPane, title, value, row, false, false);
    }


    @NotNull
    public static TextField addTextField(@NotNull GridPane gridPane, String title, String value, int row, boolean editable, boolean selectable)
    {
        gridPane.add(new Label(title), 0, row);
        @NotNull TextField textField = new TextField(value);
        gridPane.add(textField, 1, row);
        textField.setMouseTransparent(!selectable && !editable);
        textField.setEditable(editable);

        return textField;
    }

    public static void addVSpacer(@NotNull GridPane gridPane, int row)
    {
        gridPane.add(new VSpacer(10), 0, row);
    }

    @NotNull
    public static Button addButton(@NotNull GridPane gridPane, String title, int row)
    {
        @NotNull Button button = new Button(title);
        gridPane.add(button, 1, row);
        return button;
    }


}
