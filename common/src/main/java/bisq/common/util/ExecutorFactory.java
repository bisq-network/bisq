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

package bisq.common.util;

import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ExecutorFactory {
    public static final int DEFAULT_PRIORITY = 5;

    /* --------------------------------------------------------------------- */
    // Single Thread Executors
    /* --------------------------------------------------------------------- */

    public static ExecutorService newSingleThreadExecutor(String name) {
        return Executors.newSingleThreadExecutor(getThreadFactory(name));
    }
    /* --------------------------------------------------------------------- */
    // ThreadFactory
    /* --------------------------------------------------------------------- */

    public static ThreadFactory getThreadFactory(String name) {
        return new ThreadFactoryBuilder()
                .setNameFormat(name + "-%d")
                .setDaemon(true)
                .setPriority(DEFAULT_PRIORITY)
                .build();
    }

    /* --------------------------------------------------------------------- */
    // ShutdownAndAwaitTermination utils
    /* --------------------------------------------------------------------- */

    public static boolean shutdownAndAwaitTermination(ExecutorService executor, long timeoutMs) {
        return shutdownAndAwaitTermination(executor, timeoutMs, TimeUnit.MILLISECONDS);
    }

    public static boolean shutdownAndAwaitTermination(ExecutorService executor, long timeout, TimeUnit unit) {
        //noinspection UnstableApiUsage
        return MoreExecutors.shutdownAndAwaitTermination(executor, timeout, unit);
    }

}
