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

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

public class SingleThreadExecutorUtils {
    public static ExecutorService getSingleThreadExecutor(Class<?> aClass) {
        String name = aClass.getSimpleName();
        return getSingleThreadExecutor(name);
    }

    public static ExecutorService getNonDaemonSingleThreadExecutor(Class<?> aClass) {
        String name = aClass.getSimpleName();
        return getSingleThreadExecutor(name, false);
    }

    public static ExecutorService getSingleThreadExecutor(String name) {
        return getSingleThreadExecutor(name, true);
    }

    public static ListeningExecutorService getSingleThreadListeningExecutor(String name) {
        return MoreExecutors.listeningDecorator(getSingleThreadExecutor(name));
    }

    public static ExecutorService getSingleThreadExecutor(ThreadFactory threadFactory) {
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private static ExecutorService getSingleThreadExecutor(String name, boolean isDaemonThread) {
        final ThreadFactory threadFactory = getThreadFactory(name, isDaemonThread);
        return Executors.newSingleThreadExecutor(threadFactory);
    }

    private static ThreadFactory getThreadFactory(String name, boolean isDaemonThread) {
        return new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(isDaemonThread)
                .build();
    }
}
