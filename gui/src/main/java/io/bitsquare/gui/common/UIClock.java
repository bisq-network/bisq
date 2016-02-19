package io.bitsquare.gui.common;

import io.bitsquare.common.Clock;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class UIClock implements Clock {
    private static final Logger log = LoggerFactory.getLogger(UIClock.class);
    private Timer timer;

    private final List<Listener> listeners = new LinkedList<>();
    private long counter = 0;
    private long lastSecondTick;

    public UIClock() {
    }

    @Override
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
            
         /*   timer = FxTimer.runPeriodically(Duration.ofSeconds(1), () -> {
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
            });*/
        }
    }

    @Override
    public void stop() {
        timer.stop();
        timer = null;
        counter = 0;
    }

    @Override
    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }
}
