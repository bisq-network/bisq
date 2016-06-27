package io.bitsquare.gui.components.indicator.playground;

import ch.qos.logback.classic.Logger;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import javafx.application.Application;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.stage.Stage;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class Spinner1Runner extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
        Pane pane = new Pane();
        for (int i = 1; i < 2; ++i) {
            Parent node = new Spinner1(scheduler).getNode();
            node.relocate(i * 50, 30);
            pane.getChildren().add(node);
        }
        pane.setStyle("-fx-background-color: white");
        final Scene scene = new Scene(pane);
        scene.getStylesheets().setAll(
                "/io/bitsquare/gui/bitsquare.css",
                "/io/bitsquare/gui/images.css");
        stage.setScene(scene);
        stage.setOnCloseRequest(evt -> scheduler.shutdown());
        stage.setMinWidth(500);
        stage.setMinHeight(500);
        stage.show();
    }

}

class Spinner1 {
    private static final Logger log = (Logger) LoggerFactory.getLogger(Spinner1.class);
    private final Group group;
    private double rotation1, rotation2;
    private double speed1, speed2;
    private ImageView img1, img2;
    private ScheduledFuture<?> scheduled = null;
    private Timer timer;

    Spinner1(ScheduledExecutorService scheduler) {
        speed1 = 360 / 12;
        speed2 = 360 / 12;

        img1 = new ImageView();
        img1.setId("spinner");
        img2 = new ImageView();
        img2.setId("spinner");

        group = new Group(img1, img2);

        group.sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                stop();
            } else {
                play();
            }
        });
    }

    public Parent getNode() {
        return group;
    }

    private void update() {
        rotation1 += speed1;
        rotation2 -= speed2;
        img1.setRotate(rotation1);
        img2.setRotate(rotation2);
    }

    private void play() {
        stop();
        timer = UserThread.runPeriodically(this::update, 100, TimeUnit.MILLISECONDS);
    }

    private void stop() {
        if (timer != null) {
            timer.stop();
            timer = null;
        }
    }
}