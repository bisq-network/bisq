package io.bitsquare.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.TimerTask;
import java.util.concurrent.CopyOnWriteArraySet;

public class MasterTimer {
    private final static Logger log = LoggerFactory.getLogger(MasterTimer.class);
    private static final java.util.Timer timer = new java.util.Timer();
    public static long FRAME_INTERVAL_MS = 16;

    static {
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                listeners.stream().forEach(UserThread::execute);
            }
        }, FRAME_INTERVAL_MS, FRAME_INTERVAL_MS); // frame rate of 60 fps is about 16 ms
    }

    private static Set<Runnable> listeners = new CopyOnWriteArraySet<>();

    public static void addListener(Runnable runnable) {
        listeners.add(runnable);
    }

    public static void removeListener(Runnable runnable) {
        listeners.remove(runnable);
    }


}
