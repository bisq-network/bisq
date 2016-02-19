package io.bitsquare.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Random;
import java.util.TimerTask;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class DefaultJavaTimer implements Timer {
    private final Logger log = LoggerFactory.getLogger(DefaultJavaTimer.class);
    private java.util.Timer timer;

    public DefaultJavaTimer() {

    }

    @Override
    public Timer runLater(Duration delay, Runnable runnable) {
        checkArgument(timer == null, "runLater or runPeriodically already called on that timer");
        timer = new java.util.Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("TimerTask-" + new Random().nextInt(10000));
                try {
                    UserThread.execute(runnable::run);
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing timerTask failed. " + t.getMessage());
                }
            }
        }, delay.toMillis());
        return this;
    }

    @Override
    public Timer runPeriodically(java.time.Duration interval, Runnable runnable) {
        checkArgument(timer == null, "runLater or runPeriodically already called on that timer");
        timer = new java.util.Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                Thread.currentThread().setName("TimerTask-" + new Random().nextInt(10000));
                try {
                    UserThread.execute(runnable::run);
                } catch (Throwable t) {
                    t.printStackTrace();
                    log.error("Executing timerTask failed. " + t.getMessage());
                }
            }
        }, interval.toMillis(), interval.toMillis());
        return this;
    }

    @Override
    public void stop() {
        checkNotNull(timer, "Timer must not be null");
        timer.cancel();
        timer = null;
    }
}
