package io.bisq.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

// Helps configure listener objects that are run by the `UserThread` each second
// and can do per second, per minute and delayed second actions.
public class Clock {
    private static final Logger log = LoggerFactory.getLogger(Clock.class);

    public static final int IDLE_TOLERANCE = 20000;

    public interface Listener {
        void onSecondTick();

        void onMinuteTick();

        void onMissedSecondTick(long missed);
    }

    private Timer timer;
    private final List<Listener> listeners = new LinkedList<>();
    private long counter = 0;
    private long lastSecondTick;

    public Clock() {
    }

    public void start() {
        if (timer == null) {
            lastSecondTick = System.currentTimeMillis();
            timer = UserThread.runPeriodically(() -> {
                listeners.stream().forEach(Listener::onSecondTick);
                counter++;
                if (counter >= 60) {
                    counter = 0;
                    listeners.stream().forEach(Listener::onMinuteTick);
                }

                long currentTimeMillis = System.currentTimeMillis();
                long diff = currentTimeMillis - lastSecondTick;
                if (diff > 1000)
                    listeners.stream().forEach(listener -> listener.onMissedSecondTick(diff - 1000));

                lastSecondTick = currentTimeMillis;
            }, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        timer.stop();
        timer = null;
        counter = 0;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
