package io.bisq.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.UUID;

/**
 * We simulate a global frame rate timer similar to FXTimer to avoid creation of threads for each timer call.
 * Used only in headless apps like the seed node.
 */
public class FrameRateTimer implements Timer, Runnable {
    private final Logger log = LoggerFactory.getLogger(FrameRateTimer.class);

    private long interval;
    private Runnable runnable;
    private long startTs;
    private boolean isPeriodically;
    private final String uid = UUID.randomUUID().toString();
    private volatile boolean stopped;

    public FrameRateTimer() {
    }

    @Override
    public void run() {
        if (!stopped) {
            try {
                long currentTimeMillis = System.currentTimeMillis();
                if ((currentTimeMillis - startTs) >= interval) {
                    runnable.run();
                    if (isPeriodically)
                        startTs = currentTimeMillis;
                    else
                        stop();
                }
            } catch (Throwable t) {
                log.error(t.getMessage());
                t.printStackTrace();
                stop();
                throw t;
            }
        }
    }

    @Override
    public Timer runLater(Duration delay, Runnable runnable) {
        this.interval = delay.toMillis();
        this.runnable = runnable;
        startTs = System.currentTimeMillis();
        MasterTimer.addListener(this);
        return this;
    }

    @Override
    public Timer runPeriodically(Duration interval, Runnable runnable) {
        this.interval = interval.toMillis();
        isPeriodically = true;
        this.runnable = runnable;
        startTs = System.currentTimeMillis();
        MasterTimer.addListener(this);
        return this;
    }

    @Override
    public void stop() {
        stopped = true;
        MasterTimer.removeListener(this);
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FrameRateTimer)) return false;

        FrameRateTimer that = (FrameRateTimer) o;

        return !(uid != null ? !uid.equals(that.uid) : that.uid != null);

    }

    @Override
    public int hashCode() {
        return uid != null ? uid.hashCode() : 0;
    }
}
