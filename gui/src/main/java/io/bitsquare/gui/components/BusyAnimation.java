package io.bitsquare.gui.components;

import ch.qos.logback.classic.Logger;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BusyAnimation extends Pane {
    private static final Logger log = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(BusyAnimation.class);

    private Timer timer;
    private ImageView img1, img2;
    private int increment;
    private int rotation1, rotation2;
    private BooleanProperty runningProperty = new SimpleBooleanProperty();

    public BusyAnimation() {
        this(true);
    }

    public BusyAnimation(boolean isRunning) {
        runningProperty.set(isRunning);

        setMinSize(24, 24);
        setMaxSize(24, 24);
        setMouseTransparent(true);

        increment = 360 / 12;

        img1 = new ImageView();
        img1.setId("spinner");
        img2 = new ImageView();
        img2.setId("spinner");

        getChildren().addAll(img1, img2);

        sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                stop();
            else if (isRunning())
                play();
        });
        runningProperty.addListener((obs, oldVal, newVal) -> {
            if (newVal)
                play();
            else
                stop();
        });

        updateVisibility();
    }

    public void play() {
        runningProperty.set(true);

        if (timer != null)
            timer.stop();
        timer = UserThread.runPeriodically(this::updateAnimation, 100, TimeUnit.MILLISECONDS);

        updateVisibility();
    }

    public void stop() {
        runningProperty.set(false);
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        updateVisibility();
    }

    private void updateAnimation() {
        rotation1 += increment;
        rotation2 -= increment;
        img1.setRotate(rotation1);
        img2.setRotate(rotation2);
    }

    private void updateVisibility() {
        setVisible(isRunning());
        setManaged(isRunning());
    }

    public boolean isRunning() {
        return runningProperty.get();
    }

    public BooleanProperty runningProperty() {
        return runningProperty;
    }

    public void setRunning(boolean running) {
        runningProperty.set(running);
    }
}