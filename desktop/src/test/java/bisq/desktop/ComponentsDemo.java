package bisq.desktop;

import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.util.FormBuilder;

import bisq.common.util.Tuple2;

import com.jfoenix.controls.JFXTextField;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public class ComponentsDemo extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        GridPane gridPane = new GridPane();
        gridPane.getStyleClass().add("content-pane");
        gridPane.setVgap(20);

        int rowIndex = 0;

        final TitledGroupBg helloGroup = FormBuilder.addTitledGroupBg(gridPane, rowIndex++, 4, "Hello Group", 20);

        final Button button = FormBuilder.addButton(gridPane, rowIndex++, "Hello World");
        button.setDisable(true);
        button.getStyleClass().add("action-button");

        final Button buttonEnabled = FormBuilder.addButton(gridPane, rowIndex++, "Hello World");
        buttonEnabled.getStyleClass().add("action-button");

        final Tuple2<Label, InputTextField> inputTuple = FormBuilder.addLabelInputTextField(gridPane, rowIndex++, "Enter something title");
        InputTextField inputTextField = inputTuple.second;
        inputTextField.setLabelFloat(true);
        inputTextField.setPromptText("Enter something");

        final JFXTextField jfxTextField = new JFXTextField("US Dollar (USD)");
        jfxTextField.setLabelFloat(true);
        jfxTextField.setPromptText("Currency");

        final ComboBox<String> comboBox = FormBuilder.<String>addLabelComboBox(gridPane, rowIndex++, "Numbers", "Select number", 0).second;
        ObservableList list = FXCollections.observableArrayList();
        list.addAll("eins", "zwei", "drei");
        comboBox.setItems(list);

        Scene scene = new Scene(gridPane, 1000, 650);
        scene.getStylesheets().setAll(
                "/bisq/desktop/bisq.css",
                "/bisq/desktop/images.css");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
