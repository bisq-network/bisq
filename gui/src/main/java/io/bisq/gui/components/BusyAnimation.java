package io.bisq.gui.components;

import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.image.ImageView;

import java.util.concurrent.TimeUnit;

public class BusyAnimation extends ImageView {

    private Timer timer;
    private int rotation;
    private final BooleanProperty isRunningProperty = new SimpleBooleanProperty();

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
        int increment = 36;
        rotation += increment;
        setRotate(rotation);
    }

    private void updateVisibility() {
        setVisible(isRunning());
        setManaged(isRunning());
    }
}