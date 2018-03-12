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

import bisq.desktop.components.AutoTooltipButton;
import bisq.desktop.components.AutoTooltipLabel;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindingTest extends Application {
    private static final Logger log = LoggerFactory.getLogger(BindingTest.class);
    private static int counter = 0;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        VBox root = new VBox();
        root.setSpacing(20);

        Label label = new AutoTooltipLabel();
        StringProperty txt = new SimpleStringProperty();
        txt.set("-");
        label.textProperty().bind(txt);

        Button button = new AutoTooltipButton("count up");
        button.setOnAction(e -> txt.set("counter " + counter++));
        root.getChildren().addAll(label, button);


        primaryStage.setScene(new Scene(root, 400, 400));
        primaryStage.show();
    }
}
