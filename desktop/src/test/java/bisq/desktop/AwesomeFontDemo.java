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

package bisq.desktop;

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import javafx.geometry.Insets;
import javafx.geometry.Pos;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;

import java.util.Arrays;

public class AwesomeFontDemo extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Label headline = new Label("Filter by:");
        TextField input = new TextField();
        HBox filter = new HBox(10, headline, input);
        filter.setAlignment(Pos.CENTER_LEFT);
        filter.setStyle("-fx-background-color: #ddd");
        filter.setPadding(new Insets(5));

        FlowPane flowPane = new FlowPane();
        flowPane.setVgap(5);
        flowPane.setHgap(5);
        flowPane.setPadding(new Insets(5));
        flowPane.setStyle("-fx-background-color: #ddd;");
        flowPane.setHgap(5);
        flowPane.setVgap(5);

        VBox vBox = new VBox(10, filter, flowPane);

        ObservableList<AwesomeIcon> values = FXCollections.observableArrayList(Arrays.asList(AwesomeIcon.values()));
        FilteredList<AwesomeIcon> filteredList = new FilteredList<>(values);
        SortedList<AwesomeIcon> sortedList = new SortedList<>(filteredList);
        sortedList.setComparator((o1, o2) -> o1.name().compareTo(o2.name()));
        //List<AwesomeIcon> values = new ArrayList<>(Arrays.asList(AwesomeIcon.values()));
        // values.sort((o1, o2) -> o1.name().compareTo(o2.name()));
        fill(flowPane, sortedList);

        input.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredList.setPredicate(e -> {
                return newValue == null ||
                        newValue.isEmpty() ||
                        e.name().toLowerCase().contains(newValue.toLowerCase());
            });
            fill(flowPane, sortedList);
        });

        primaryStage.setScene(new Scene(vBox, 1200, 950));
        primaryStage.show();
    }

    private void fill(FlowPane flowPane, SortedList<AwesomeIcon> sortedList) {
        flowPane.getChildren().clear();
        for (AwesomeIcon icon : sortedList) {
            Label label = new Label();
            Button button = new Button(icon.name(), label);
            button.setStyle("-fx-background-color: #fff;");
            AwesomeDude.setIcon(label, icon, "12");
            flowPane.getChildren().add(button);
        }
    }
}
