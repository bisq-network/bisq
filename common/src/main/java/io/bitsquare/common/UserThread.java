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

package io.bitsquare.common;

import com.google.common.util.concurrent.MoreExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class UserThread {
    private static final Logger log = LoggerFactory.getLogger(UserThread.class);

    public static Executor getExecutor() {
        return executor;
    }

    public static void setExecutor(Executor executor) {
        UserThread.executor = executor;
    }

    static {
        // If not defined we use same thread as caller thread
        executor = MoreExecutors.directExecutor();
    }

    private static Executor executor;

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
        Timer timer = new Timer();
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
        }, timeUnit.toMillis(delay));
        return timer;
    }
}
