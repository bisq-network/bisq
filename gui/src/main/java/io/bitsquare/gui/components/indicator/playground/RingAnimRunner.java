package io.bitsquare.gui.components.indicator.playground;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class RingAnimRunner extends Application {
    private static final Logger log = LoggerFactory.getLogger(RingAnimRunner.class);

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        Pane pane = new Pane();
        for (int i = 1; i < 2; ++i) {
            Parent node = new Animation(scheduler, 6.0, 1.0).getNode();

            //  ProgressIndicator node = new ProgressIndicator(-1);
            // StaticProgressIndicator node = new StaticProgressIndicator(-1);
            //node.setMinSize(324, 324);
            node.relocate(i * 50, 30);
            pane.getChildren().add(node);
        }

       /* for (int i = 0; i < 10; ++i) {
            ProgressIndicator node = new ProgressIndicator(-1);
            node.setMaxWidth(20);
            node.setMaxHeight(20);
            node.relocate(i * 50, 30);
            pane.getChildren().add(node);
        }*/
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

class Animation {
    private final Group group;
    private final ScheduledExecutorService scheduler;
    private final long periodMs;
    private final int steps;
    private final Arc arc1, arc2;
    private boolean fw;
    private int step;
    private int rotationStep;
    private int speed;
    private double opacity1, opacity2;
    private ScheduledFuture<?> scheduled = null;

    Animation(ScheduledExecutorService scheduler, double fps, double durationSeconds) {
        this.scheduler = scheduler;
        this.periodMs = (long) (1000 * durationSeconds / fps);
        this.steps = (int) (fps * durationSeconds);
        speed = (int) (360.0 / steps);
        double x = 30;
        double y = 30;
        double radius1 = 11;
        double radius2 = 8;

        arc1 = new Arc(x, y, radius1, radius1, 0, 270);
        arc1.setType(ArcType.OPEN);
        arc1.setStroke(Color.rgb(0x66, 0x66, 0x66));
        arc1.setFill(null);
        arc1.setStrokeWidth(1);

        arc2 = new Arc(x, y, radius2, radius2, 90, 270);
        arc2.setType(ArcType.OPEN);
        arc2.setStroke(Color.rgb(0x0f, 0x87, 0xc3));
        arc2.setFill(null);
        arc2.setStrokeWidth(1);

        group = new Group(arc1, arc2);
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

    private void step() {
        rotationStep += speed;
        step += fw ? 1 : -1;

        if (step == steps) {
            fw = false;
            opacity1 = 0.0;
            opacity2 = 1.0;
        } else if (step == 0) {
            fw = true;
            opacity1 = 1.0;
            opacity2 = 0.0;
        } else {
            opacity1 = 1.0 * (steps - step) / steps;
            opacity2 = 1.0 * step / steps;
        }

        arc1.setOpacity(opacity1);
        arc2.setOpacity(opacity2);

        arc1.setRotate(rotationStep);
        arc2.setRotate(rotationStep * -1);
    }

    private void play() {
        stop();
        fw = true;
        step = 0;
        rotationStep = 0;
        scheduled = scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::step), periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stop() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }
}