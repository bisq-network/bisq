package io.bitsquare.gui.components;

import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.ImageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class BusyAnimation extends ImageView {
    private static final Logger log = LoggerFactory.getLogger(BusyAnimation.class);

    private Timer timer;
    private final int increment = 36;
    private int rotation;
    private BooleanProperty isRunningProperty = new SimpleBooleanProperty();

    public BusyAnimation() {
        this(true);
    }

    public BusyAnimation(boolean isRunning) {
        isRunningProperty.set(isRunning);

        setMouseTransparent(true);
        setId("spinner");

        sceneProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null)
                stop();
            else if (isRunning())
                play();
        });
        isRunningProperty.addListener((obs, oldVal, newVal) -> {
            if (newVal)
                play();
            else
                stop();
        });

        updateVisibility();
    }

    public void play() {
        isRunningProperty.set(true);

        if (timer != null)
            timer.stop();
        timer = UserThread.runPeriodically(this::updateAnimation, 100, TimeUnit.MILLISECONDS);

        updateVisibility();
    }

    public void stop() {
        isRunningProperty.set(false);
        if (timer != null) {
            timer.stop();
            timer = null;
        }
        updateVisibility();
    }

    public boolean isRunning() {
        return isRunningProperty.get();
    }

    public BooleanProperty isRunningProperty() {
        return isRunningProperty;
    }

    public void setIsRunning(boolean isRunning) {
        isRunningProperty.set(isRunning);
    }

    private void updateAnimation() {
        rotation += increment;
        setRotate(rotation);
    }

    private void updateVisibility() {
        setVisible(isRunning());
        setManaged(isRunning());
    }
}