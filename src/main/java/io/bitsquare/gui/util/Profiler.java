/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.util;

import com.google.common.base.Stopwatch;

import java.util.concurrent.TimeUnit;

import javafx.animation.AnimationTimer;
import javafx.scene.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Profiler {
    private static final Logger log = LoggerFactory.getLogger(Profiler.class);

    private static final Stopwatch globalStopwatch = Stopwatch.createStarted();
    private static final ThreadLocal<Stopwatch> threadStopwatch = ThreadLocal.withInitial(Stopwatch::createStarted);
    private static final ThreadLocal<Long> last = ThreadLocal.withInitial(() -> 0L);
    private static long lastFPSTime = System.currentTimeMillis();

    public static void printMsgWithTime(String msg) {
        final long elapsed = threadStopwatch.get().elapsed(TimeUnit.MILLISECONDS);
        log.trace("\n\nCalled by: {} \nElapsed time: {}ms \nTotal time:   {}ms\n\n",
                msg, elapsed - last.get(), globalStopwatch.elapsed(TimeUnit.MILLISECONDS));

        last.set(elapsed);
    }

    public static void init() {
        AnimationTimer fpsTimer = new AnimationTimer() {
            @Override
            public void handle(long l) {
                long elapsed = (System.currentTimeMillis() - lastFPSTime);
                if (elapsed > 50)
                    log.trace("Profiler: last frame used {}ms", elapsed);

                lastFPSTime = System.currentTimeMillis();
            }
        };
        fpsTimer.start();
    }

    public static void initScene(Scene scene) {
       /* PerformanceTracker tracker = PerformanceTracker.getSceneTracker(scene);
        Timeline timeline = new Timeline(
                new KeyFrame(Duration.seconds(1), t -> {
                    log.trace("FPS (tracker.getAverageFPS) = " + tracker.getAverageFPS());
                }));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();*/
    }
}
