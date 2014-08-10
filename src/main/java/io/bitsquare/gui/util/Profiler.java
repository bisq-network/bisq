package io.bitsquare.gui.util;

import com.google.common.base.Stopwatch;
import java.util.concurrent.TimeUnit;
import javafx.animation.AnimationTimer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Profiler
{
    private static final Logger log = LoggerFactory.getLogger(Profiler.class);

    private static final Stopwatch globalStopwatch = Stopwatch.createStarted();
    private static final ThreadLocal<Stopwatch> threadStopwatch = ThreadLocal.withInitial(Stopwatch::createStarted);
    private static final ThreadLocal<Long> last = ThreadLocal.withInitial(() -> 0L);
    private static long lastCurrentTimeMillis = System.currentTimeMillis();
    private static long lastFPSTime = System.currentTimeMillis();
    private static long counter = 0;

    public static void printMsgWithTime(String msg)
    {
        final long elapsed = threadStopwatch.get().elapsed(TimeUnit.MILLISECONDS);
        log.trace("Msg: {} elapsed: {}ms / total time:[globalStopwatch: {}ms / threadStopwatch: {}ms / currentTimeMillis: {}ms]",
                  msg,
                  elapsed - last.get(),
                  globalStopwatch.elapsed(TimeUnit.MILLISECONDS),
                  elapsed,
                  System.currentTimeMillis() - lastCurrentTimeMillis);
        lastCurrentTimeMillis = System.currentTimeMillis();
        last.set(elapsed);
    }

    public static void init()
    {
        AnimationTimer fpsTimer = new AnimationTimer()
        {
            @Override
            public void handle(long l)
            {
                counter++;
                long elapsed = (System.currentTimeMillis() - lastFPSTime);
                if (elapsed > 19)
                    log.trace("FPS: elapsed: {}ms / FPS total counter: {}", elapsed, counter);

                lastFPSTime = System.currentTimeMillis();
            }
        };
        fpsTimer.start();
    }
}
