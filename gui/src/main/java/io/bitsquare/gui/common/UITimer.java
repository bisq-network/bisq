package io.bitsquare.gui.common;

import io.bitsquare.common.Timer;
import org.reactfx.util.FxTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class UITimer implements Timer {
    private final Logger log = LoggerFactory.getLogger(UITimer.class);
    private org.reactfx.util.Timer timer;

    public UITimer() {
    }

    @Override
    public Timer runLater(Duration delay, Runnable runnable) {
        checkArgument(timer == null, "runLater or runPeriodically already called on that timer");
        timer = FxTimer.create(delay, runnable);
        timer.restart();
        return this;
    }

    @Override
    public Timer runPeriodically(Duration interval, Runnable runnable) {
        checkArgument(timer == null, "runLater or runPeriodically already called on that timer");
        timer = FxTimer.createPeriodic(interval, runnable);
        timer.restart();
        return this;
    }

    @Override
    public void stop() {
        checkNotNull(timer, "Timer must not be null");
        timer.stop();
        timer = null;
    }
}
