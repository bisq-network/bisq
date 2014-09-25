/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.image.*;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.controlsfx.control.PopOver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NotificationTest extends Application {
    private static final Logger log = LoggerFactory.getLogger(NotificationTest.class);
    private Scene notificationScene;
    private Stage notificationStage;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Pane view = new StackPane();
        Button b = new Button("open");
        b.setOnAction(e -> addItem());
        view.getChildren().addAll(b);
        Scene scene = new Scene(view, 1000, 750);
        scene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/bitsquare.css").toExternalForm(),
                getClass().getResource("/io/bitsquare/gui/images.css").toExternalForm());

        primaryStage.setScene(scene);
        primaryStage.setMinWidth(750);
        primaryStage.setMinHeight(500);
        primaryStage.show();
        initNotification();
    }

    private List<PopOver> popOvers = new ArrayList<>();

    private HBox getNotificationItem(String headline, String info) {
        Label headlineLabel = new Label(headline);
        Label infoLabel = new Label(info);
        ImageView icon = new ImageView();
        icon.setId("image-info");

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(10, 10, 10, 10));
        vBox.setSpacing(10);
        vBox.getChildren().addAll(headlineLabel, infoLabel);
        HBox hBox = new HBox();
        hBox.setPadding(new Insets(10, 10, 10, 10));
        hBox.setSpacing(10);
        hBox.getChildren().addAll(icon, vBox);
        return hBox;
    }

    private void addItem() {
        HBox hBox = getNotificationItem("Headline " + new Random().nextInt(), "test " + new Random().nextInt());
        PopOver popOver = new PopOver(hBox);
        popOver.setDetachable(false);
        popOver.setArrowSize(0);
        popOver.setPrefSize(200, 100);
        popOver.show(notificationScene.getWindow(), Screen.getPrimary().getBounds().getWidth() - 200, 0);
        popOvers.add(popOver);

        // Add a timeline for popup fade out
        KeyValue fadeOutBegin = new KeyValue(popOver.opacityProperty(), 1.0);
        KeyValue fadeOutEnd = new KeyValue(popOver.opacityProperty(), 0.0);

        KeyFrame kfBegin = new KeyFrame(Duration.ZERO, fadeOutBegin);
        KeyFrame kfEnd = new KeyFrame(Duration.millis(5000), fadeOutEnd);

        Timeline timeline = new Timeline(kfBegin, kfEnd);
        timeline.setDelay(Duration.millis(500));
        timeline.setOnFinished(actionEvent -> Platform.runLater(() -> {
            popOvers.remove(popOver);
        }));

        if (notificationStage.isShowing()) {
            notificationStage.toFront();
        }
        else {
            notificationStage.show();
        }

        popOver.show(notificationStage);
        timeline.play();
    }

    private void initNotification() {
        Region region = new Region();
        region.setMouseTransparent(true);
        region.setStyle("-fx-background-color:transparent;");
        region.setPrefSize(1, 1);

        notificationScene = new Scene(region);
        notificationScene.setFill(Color.TRANSPARENT);
        notificationScene.getStylesheets().setAll(getClass().getResource("/io/bitsquare/gui/bitsquare.css")
                        .toExternalForm(),
                getClass().getResource("/io/bitsquare/gui/images.css").toExternalForm());

        notificationStage = new Stage();
        notificationStage.initStyle(StageStyle.TRANSPARENT);
        notificationStage.setScene(notificationScene);
    }

    @Override
    public void stop() throws Exception {
    }
}
