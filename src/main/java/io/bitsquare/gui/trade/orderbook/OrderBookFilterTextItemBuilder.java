package io.bitsquare.gui.trade.orderbook;

import io.bitsquare.gui.components.VSpacer;
import io.bitsquare.gui.util.Icons;
import io.bitsquare.settings.Settings;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

import java.util.*;

public class OrderBookFilterTextItemBuilder
{

    public static void build(Pane parent, String title, List<String> values, List<String> allValues)
    {
        final Pane pane = new Pane();
        pane.setPrefHeight(23);

        final Label titleLabel = new Label(title);
        titleLabel.setLayoutY(4);
        titleLabel.setId("form-title");

        FlowPane flowPane = new FlowPane();

        double xPos = 170.0;
        double yPos = 5.0;

        List<String> openValues = new ArrayList<>(allValues);
        openValues.removeAll(values);
        ObservableList<String> observableList = FXCollections.observableArrayList(openValues);
        Collections.sort(observableList);

        ComboBox comboBox = new ComboBox(observableList);
        comboBox.setLayoutX(xPos);
        comboBox.setLayoutY(yPos);
        comboBox.setClip(Icons.getIconImageView(Icons.ADD));
        comboBox.setValue(Settings.getCurrency());

        comboBox.valueProperty().addListener(new ChangeListener<Object>()
        {
            @Override
            public void changed(ObservableValue ov, Object oldValue, Object newValue)
            {
                if (newValue != null)
                {
                    String value;
                    if (newValue instanceof Currency)
                        value = ((Currency) newValue).getCurrencyCode();
                    else
                        value = (String) newValue;

                    if (flowPane.getChildren().size() > 0)
                    {
                        Pane lastItem = (Pane) flowPane.getChildren().get(flowPane.getChildren().size() - 1);
                        Button button = (Button) lastItem.getChildren().get(0);
                        button.setText(button.getText().substring(0, button.getText().length() - 2) + ", ");
                    }

                    addRemovableItem(flowPane, value + "  ", observableList);
                    comboBox.getSelectionModel().clearSelection();
                    observableList.remove(newValue);
                }
            }
        });

        // combobox does not support icon (mask background with icon), so we need a graphic here
        ImageView addImageView = Icons.getIconImageView(Icons.ADD);
        addImageView.setLayoutX(xPos);
        addImageView.setLayoutY(yPos);
        addImageView.setMouseTransparent(true);

        pane.getChildren().addAll(titleLabel, comboBox, addImageView);

        Iterator<String> iterator = values.iterator();
        for (Iterator<String> stringIterator = iterator; stringIterator.hasNext(); )
        {
            String value = stringIterator.next();
            if (stringIterator.hasNext())
                addRemovableItem(flowPane, value + ", ", observableList);
            else
                addRemovableItem(flowPane, value + "  ", observableList);
        }

        parent.getChildren().addAll(pane, flowPane, new VSpacer(3), new Separator(), new VSpacer(10));
    }

    private static void addRemovableItem(FlowPane flowPane, String text, ObservableList<String> observableList)
    {
        Pane pane = new Pane();

        Button icon = new Button("", Icons.getIconImageView(Icons.REMOVE));
        icon.setStyle("-fx-background-color: transparent;");
        icon.setPadding(new Insets(-5.0, 0.0, 0.0, 0.0));
        icon.setVisible(false);

        Button button = new Button(text);
        button.setStyle("-fx-background-color: transparent;");
        button.setPadding(new Insets(0.0, 0.0, 0.0, 0.0));

        pane.setOnMouseEntered(e -> {
            icon.setVisible(true);
            icon.setLayoutX(button.getWidth() - 7);
        });
        pane.setOnMouseExited(e -> {
            icon.setVisible(false);
            icon.setLayoutX(0);
        });
        icon.setOnAction(e -> {
            flowPane.getChildren().remove(button.getParent());

            observableList.add(text);
            Collections.sort(observableList);

            if (flowPane.getChildren().size() > 0)
            {
                Pane lastItem = (Pane) flowPane.getChildren().get(flowPane.getChildren().size() - 1);
                Button lastButton = (Button) lastItem.getChildren().get(0);
                lastButton.setText(lastButton.getText().substring(0, lastButton.getText().length() - 2) + "  ");
            }
        });
        pane.getChildren().addAll(button, icon);
        flowPane.getChildren().add(pane);
    }
}
