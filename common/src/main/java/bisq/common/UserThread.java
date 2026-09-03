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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;

import java.time.Duration;

import java.util.Random;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import java.lang.reflect.InvocationTargetException;

import lombok.Getter;
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
    private static volatile Class<? extends Timer> timerClass;
    private static Class<? extends Timer> shutdownTimerClass;
    @Getter
    private static volatile Executor executor;
    private static Executor shutdownExecutor;
    private static boolean shutdownExecutorConfigured;
    private static volatile boolean jvmShutdownInProgress;

    public static synchronized void setTimerClass(Class<? extends Timer> timerClass) {
        UserThread.timerClass = timerClass;
        if (!shutdownExecutorConfigured) {
            shutdownTimerClass = timerClass;
        }
    }

    public static synchronized void setExecutor(Executor executor) {
        UserThread.executor = executor;
        if (!shutdownExecutorConfigured) {
            shutdownExecutor = executor;
        }
    }

    /**
     * Configures a UserThread implementation which does not depend on application-framework
     * infrastructure that can be disposed by a concurrent JVM shutdown hook.
     * <p>
     * An explicitly configured shutdown executor wins over the regular executor for the rest of the
     * process lifetime. Otherwise a later {@link #setExecutor} or {@link #setTimerClass} call would
     * silently restore the framework executor as the shutdown executor, which is only observable
     * when the process is terminated externally.
     */
    public static synchronized void setShutdownExecutor(Executor executor,
                                                        Class<? extends Timer> timerClass) {
        shutdownExecutor = executor;
        shutdownTimerClass = timerClass;
        shutdownExecutorConfigured = true;
    }

    static {
        // If not defined we use same thread as caller thread
        executor = MoreExecutors.directExecutor();
        timerClass = FrameRateTimer.class;
        shutdownExecutor = executor;
        shutdownTimerClass = timerClass;
    }

    public static void execute(Runnable command) {
        UserThread.executor.execute(command);
    }

    /**
     * Moves all subsequent UserThread work to the shutdown-safe executor before dispatching
     * the first external-shutdown task. Persistence completion callbacks also use UserThread,
     * so the replacement must remain available until graceful shutdown has completed.
     */
    public static void executeAtShutdown(Runnable command) {
        Executor executor;
        synchronized (UserThread.class) {
            jvmShutdownInProgress = true;
            UserThread.executor = shutdownExecutor;
            timerClass = shutdownTimerClass;
            executor = UserThread.executor;
        }
        executor.execute(command);
    }

    public static boolean isJvmShutdownInProgress() {
        return jvmShutdownInProgress;
    }

    @VisibleForTesting
    public static synchronized void resetForTests() {
        executor = MoreExecutors.directExecutor();
        timerClass = FrameRateTimer.class;
        shutdownExecutor = executor;
        shutdownTimerClass = timerClass;
        shutdownExecutorConfigured = false;
        jvmShutdownInProgress = false;
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
