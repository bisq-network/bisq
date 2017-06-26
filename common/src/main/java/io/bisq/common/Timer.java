package io.bisq.common;

import java.time.Duration;

public interface Timer {
    Timer runLater(java.time.Duration delay, Runnable action);

    Timer runPeriodically(Duration interval, Runnable runnable);

    void stop();
}
