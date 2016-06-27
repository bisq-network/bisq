package io.bitsquare.gui.components.indicator.playground;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;


public class ProgressIndicatorRunner extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        Pane pane = new Pane();
        for (int i = 1; i < 2; ++i) {
            ProgressIndicator node = new ProgressIndicator(-1);
            node.setMaxSize(24, 24);
            node.relocate(i * 50, 30);
            pane.getChildren().add(node);
        }
        pane.setStyle("-fx-background-color: white");
        final Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.setOnCloseRequest(evt -> scheduler.shutdown());
        stage.setMinWidth(500);
        stage.setMinHeight(500);
        stage.show();
    }
}