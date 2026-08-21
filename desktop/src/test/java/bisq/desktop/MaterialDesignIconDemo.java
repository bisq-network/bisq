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

import de.jensd.fx.glyphs.materialdesignicons.MaterialDesignIcon;
import de.jensd.fx.glyphs.materialdesignicons.utils.MaterialDesignIconFactory;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class MaterialDesignIconDemo extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToWidth(true);

        FlowPane flowPane = new FlowPane();
        flowPane.setStyle("-fx-background-color: #ddd;");
        flowPane.setHgap(2);
        flowPane.setVgap(2);
        List<MaterialDesignIcon> values = new ArrayList<>(Arrays.asList(MaterialDesignIcon.values()));
        values.sort(Comparator.comparing(Enum::name));
        for (MaterialDesignIcon icon : values) {
            Button button = MaterialDesignIconFactory.get().createIconButton(icon, icon.name());
            flowPane.getChildren().add(button);
        }

        scrollPane.setContent(flowPane);

        primaryStage.setScene(new Scene(scrollPane, 1200, 950));
        primaryStage.show();
    }
}
