package io.bitsquare.gui.components.indicator.playground;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class BitSquareProgressIndicator extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
        Pane pane = new Pane();
        for (int i = 1; i < 2; ++i) {
            Parent node = new Indicator(scheduler, 6.0, 1.0).getNode();
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

        stage.setScene(new Scene(pane));
        stage.setOnCloseRequest(evt -> scheduler.shutdown());
        stage.setMinWidth(500);
        stage.setMinHeight(200);
        stage.show();
    }

}

class Indicator {

    private static final Color lColor = Color.rgb(0x66, 0x66, 0x66);
    private static final Color rColor = Color.rgb(0x0f, 0x87, 0xc3);

    private static final PathElement[] ELEMS = new PathElement[]{
            new MoveTo(9.2362945, 19.934046),
            new CubicCurveTo(-1.3360939, -0.28065, -1.9963146, -1.69366, -1.9796182, -2.95487),
            new CubicCurveTo(-0.1152909, -1.41268, -0.5046634, -3.07081, -1.920768, -3.72287),
            new CubicCurveTo(-1.4711631, -0.77284, -3.4574873, -0.11153, -4.69154031, -1.40244),
            new CubicCurveTo(-1.30616123, -1.40422, -0.5308003, -4.1855799, 1.46313121, -4.4219799),
            new CubicCurveTo(1.4290018, -0.25469, 3.1669517, -0.0875, 4.1676818, -1.36207),
            new CubicCurveTo(0.9172241, -1.12206, 0.9594176, -2.63766, 1.0685793, -4.01259),
            new CubicCurveTo(0.4020299, -1.95732999, 3.2823027, -2.72818999, 4.5638567, -1.15760999),
            new CubicCurveTo(1.215789, 1.31824999, 0.738899, 3.90740999, -1.103778, 4.37267999),
            new CubicCurveTo(-1.3972543, 0.40868, -3.0929979, 0.0413, -4.2208253, 1.16215),
            new CubicCurveTo(-1.3524806, 1.26423, -1.3178578, 3.29187, -1.1086673, 4.9895199),
            new CubicCurveTo(0.167826, 1.28946, 1.0091133, 2.5347, 2.3196964, 2.86608),
            new CubicCurveTo(1.6253079, 0.53477, 3.4876372, 0.45004, 5.0294052, -0.30121),
            new CubicCurveTo(1.335829, -0.81654, 1.666839, -2.49408, 1.717756, -3.9432),
            new CubicCurveTo(0.08759, -1.1232899, 0.704887, -2.3061299, 1.871843, -2.5951699),
            new CubicCurveTo(1.534558, -0.50726, 3.390804, 0.62784, 3.467269, 2.28631),
            new CubicCurveTo(0.183147, 1.4285099, -0.949563, 2.9179999, -2.431156, 2.9383699),
            new CubicCurveTo(-1.390597, 0.17337, -3.074035, 0.18128, -3.971365, 1.45069),
            new CubicCurveTo(-0.99314, 1.271, -0.676157, 2.98683, -1.1715, 4.43018),
            new CubicCurveTo(-0.518248, 1.11436, -1.909118, 1.63902, -3.0700005, 1.37803),
            new ClosePath()
    };

    static {
        for (int i = 1; i < ELEMS.length; ++i) {
            ELEMS[i].setAbsolute(false);
        }
    }

    private final Path left;
    private final Path right;
    private final Group g;
    private final ScheduledExecutorService scheduler;
    private final long periodMs;
    private final int steps;

    private boolean fw;
    private int step;
    private ScheduledFuture<?> scheduled = null;

    Indicator(ScheduledExecutorService scheduler, double fps, double durationSeconds) {
        this.scheduler = scheduler;
        this.periodMs = (long) (1000 * durationSeconds / fps);
        this.steps = (int) (fps * durationSeconds);

        left = new Path(ELEMS);
        right = new Path(ELEMS);

        right.setScaleX(-1);
        right.setScaleY(-1);
        right.setTranslateX(7.266);
        right.setOpacity(0.0);

        left.setStroke(null);
        right.setStroke(null);
        left.setFill(lColor);
        right.setFill(rColor);

        g = new Group(left, right);

        g.sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                stop();
            } else {
                play();
            }
        });
    }

    public Parent getNode() {
        return g;
    }

    private void step() {
        double lOpacity, rOpacity;

        step += fw ? 1 : -1;

        if (step == steps) {
            fw = false;
            lOpacity = 0.0;
            rOpacity = 1.0;
        } else if (step == 0) {
            fw = true;
            lOpacity = 1.0;
            rOpacity = 0.0;
        } else {
            lOpacity = 1.0 * (steps - step) / steps;
            rOpacity = 1.0 * step / steps;
        }

        left.setOpacity(lOpacity);
        right.setOpacity(rOpacity);
    }

    private void play() {
        stop();
        fw = true;
        step = 0;
        scheduled = scheduler.scheduleAtFixedRate(() -> Platform.runLater(this::step), periodMs, periodMs, TimeUnit.MILLISECONDS);
    }

    private void stop() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
    }

}