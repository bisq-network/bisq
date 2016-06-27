package io.bitsquare.gui.components.indicator.playground;

import io.bitsquare.gui.components.BusyAnimation;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

public class BusyAnimationRunner extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        Pane pane = new Pane();
        for (int i = 1; i < 2; ++i) {
            BusyAnimation node = new BusyAnimation();
            node.relocate(i * 50, 30);
            pane.getChildren().add(node);
        }
        pane.setStyle("-fx-background-color: white");
        final Scene scene = new Scene(pane);
        scene.getStylesheets().setAll(
                "/io/bitsquare/gui/bitsquare.css",
                "/io/bitsquare/gui/images.css");
        stage.setScene(scene);
        stage.setMinWidth(500);
        stage.setMinHeight(500);
        stage.show();
    }
}
