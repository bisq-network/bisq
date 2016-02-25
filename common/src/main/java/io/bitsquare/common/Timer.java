package io.bitsquare.common;

import java.time.Duration;

public interface Timer {
    boolean STRESS_TEST = false;

    Timer runLater(java.time.Duration delay, Runnable action);

    Timer runPeriodically(Duration interval, Runnable runnable);

    void stop();
}
