package io.bisq.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;

// Runs all listener objects periodically in a short interval.
public class MasterTimer {
    private final static Logger log = LoggerFactory.getLogger(MasterTimer.class);
    private static final java.util.Timer timer = new java.util.Timer();
    // frame rate of 60 fps is about 16 ms but we  don't need such a short interval, 100 ms should be good enough
    public static final long FRAME_INTERVAL_MS = 100;

    static {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                UserThread.execute(() -> listeners.stream().forEach(Runnable::run));
            }
        }, FRAME_INTERVAL_MS, FRAME_INTERVAL_MS);
    }

    private static final Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    public static void addListener(Runnable runnable) {
        listeners.add(runnable);
    }

    public static void removeListener(Runnable runnable) {
        listeners.remove(runnable);
    }
}
