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

import de.jensd.fx.fontawesome.AwesomeDude;
import de.jensd.fx.fontawesome.AwesomeIcon;

import javafx.application.Application;

import javafx.stage.Stage;

import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AwesomeFontDemo extends Application {
    private static final Logger log = LoggerFactory.getLogger(AwesomeFontDemo.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Pane root = new FlowPane();
        List<AwesomeIcon> values = new ArrayList<>(Arrays.asList(AwesomeIcon.values()));
        values.sort((o1, o2) -> o1.name().compareTo(o2.name()));
        for (AwesomeIcon icon : values) {
            Label label = new AutoTooltipLabel();
            Button button = new AutoTooltipButton(icon.name(), label);
            AwesomeDude.setIcon(label, icon);
            root.getChildren().add(button);
        }

        primaryStage.setScene(new Scene(root, 900, 850));
        primaryStage.show();
    }
}
