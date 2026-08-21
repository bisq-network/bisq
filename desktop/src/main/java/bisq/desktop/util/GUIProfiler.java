/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.desktop.util;

import com.google.common.base.Stopwatch;

import javafx.animation.AnimationTimer;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GUIProfiler {
    private static final Logger log = LoggerFactory.getLogger(GUIProfiler.class);

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
}
