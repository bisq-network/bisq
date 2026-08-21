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

package bisq.common;

import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import java.lang.reflect.InvocationTargetException;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;


/**
 * Defines which thread is used as user thread. The user thread is the the main thread in the single threaded context.
 * For JavaFX it is usually the Platform::RunLater executor, for a headless application it is any single threaded
 * executor.
 * Additionally sets a timer class so JavaFX and headless applications can set different timers (UITimer for JavaFX
 * otherwise we use the default FrameRateTimer).
 * <p>
 * Provides also methods for delayed and periodic executions.
 */
@Slf4j
public class UserThread {
    private static Class<? extends Timer> timerClass;
    @Getter
    @Setter
    private static Executor executor;

    public static void setTimerClass(Class<? extends Timer> timerClass) {
        UserThread.timerClass = timerClass;
    }

    static {
        // If not defined we use same thread as caller thread
        executor = MoreExecutors.directExecutor();
        timerClass = FrameRateTimer.class;
    }

    public static void execute(Runnable command) {
        UserThread.executor.execute(command);
    }

    // Prefer FxTimer if a delay is needed in a JavaFx class (gui module)
    public static Timer runAfterRandomDelay(Runnable runnable, long minDelayInSec, long maxDelayInSec) {
        return UserThread.runAfterRandomDelay(runnable, minDelayInSec, maxDelayInSec, TimeUnit.SECONDS);
    }

    @SuppressWarnings("WeakerAccess")
    public static Timer runAfterRandomDelay(Runnable runnable, long minDelay, long maxDelay, TimeUnit timeUnit) {
        return UserThread.runAfter(runnable, new Random().nextInt((int) (maxDelay - minDelay)) + minDelay, timeUnit);
    }

    public static Timer runAfter(Runnable runnable, long delayInSec) {
        return UserThread.runAfter(runnable, delayInSec, TimeUnit.SECONDS);
    }

    public static Timer runAfter(Runnable runnable, long delay, TimeUnit timeUnit) {
        return getTimer().runLater(Duration.ofMillis(timeUnit.toMillis(delay)), runnable);
    }

    public static Timer runPeriodically(Runnable runnable, long intervalInSec) {
        return UserThread.runPeriodically(runnable, intervalInSec, TimeUnit.SECONDS);
    }

    public static Timer runPeriodically(Runnable runnable, long interval, TimeUnit timeUnit) {
        return getTimer().runPeriodically(Duration.ofMillis(timeUnit.toMillis(interval)), runnable);
    }

    private static Timer getTimer() {
        try {
            return timerClass.getDeclaredConstructor().newInstance();
        } catch (InstantiationException | NoSuchMethodException | InvocationTargetException | IllegalAccessException e) {
            String message = "Could not instantiate timer bsTimerClass=" + timerClass;
            log.error(message, e);
            throw new RuntimeException(message);
        }
    }
}
