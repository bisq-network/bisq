package bisq.desktop;

import bisq.desktop.components.FundsTextField;
import bisq.desktop.components.InfoInputTextField;
import bisq.desktop.components.InputTextField;
import bisq.desktop.components.TitledGroupBg;
import bisq.desktop.util.FormBuilder;
import bisq.desktop.util.Layout;

import bisq.core.locale.CryptoCurrency;
import bisq.core.locale.GlobalSettings;
import bisq.core.locale.Res;

import bisq.common.util.Tuple2;
import bisq.common.util.Tuple3;

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;
import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIconView;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.text.Text;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import static bisq.desktop.util.FormBuilder.addFundsTextfield;
import static bisq.desktop.util.FormBuilder.getIconForLabel;

public class ComponentsDemo extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        final CryptoCurrency btc = new CryptoCurrency("BTC", "bitcoin");
        GlobalSettings.setDefaultTradeCurrency(btc);
        Res.setup();

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
        inputTextField.setText("Hello");
        inputTextField.setPromptText("Enter something");

        final Tuple3<HBox, InfoInputTextField, Label> editableValueBox = FormBuilder.getEditableValueBoxWithInfo("Please Enter!");
        final HBox box = editableValueBox.first;
//        box.setMaxWidth(243);
        //box.setMaxWidth(200);
        editableValueBox.third.setText("BTC");
        editableValueBox.second.setContentForInfoPopOver(new Label("Hello World!"));
        GridPane.setRowIndex(box, rowIndex++);
        GridPane.setColumnIndex(box, 1);
        gridPane.getChildren().add(box);

        final FundsTextField fundsTextField = addFundsTextfield(gridPane, rowIndex++,
                "Total Needed", Layout.FIRST_ROW_AND_GROUP_DISTANCE);
        //fundsTextField.setText("Hello World");

        final ComboBox<String> comboBox = FormBuilder.<String>addTopLabelComboBox(gridPane, rowIndex++, "Numbers","Select numbers",0).second;
        ObservableList list = FXCollections.observableArrayList();
        list.addAll("eins", "zwei", "drei");
        comboBox.setItems(list);

        Label xLabel = new Label();
//        xLabel.getStyleClass().add("opaque-icon");
        //final Text icon = getIconForLabel(MaterialDesignIcon.CLOSE, "2em", xLabel);
        final Text icon = getIconForLabel(MaterialDesignIcon.DRAG_HORIZONTAL, "2em", xLabel);
        icon.getStyleClass().add("opaque-icon");
        GridPane.setRowIndex(xLabel, rowIndex++);
        GridPane.setColumnIndex(xLabel, 0);
        gridPane.getChildren().add(xLabel);

//        FlowPane iconsPane = new FlowPane(3,3);
//        for (MaterialDesignIcon ic : MaterialDesignIcon.values()) {
//            iconsPane.getChildren().add(new MaterialDesignIconView(ic, "3em"));
//        }
//
//        GridPane.setRowIndex(iconsPane, rowIndex++);
//        gridPane.getChildren().add(iconsPane);

        Scene scene = new Scene(gridPane, 1000, 650);
        scene.getStylesheets().setAll(
                "/bisq/desktop/bisq.css",
                "/bisq/desktop/images.css");
        primaryStage.setScene(scene);
        primaryStage.show();
    }
}
