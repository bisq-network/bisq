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

package bisq.apitest.botsupport.util;

import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BotUtilities {

    // An unfortunate duplication of code in non-accessible :common Utilities class.

    public static ListeningExecutorService getListeningExecutorService(String name,
                                                                       int corePoolSize,
                                                                       int maximumPoolSize,
                                                                       long keepAliveTimeInSec) {
        return MoreExecutors.listeningDecorator(getThreadPoolExecutor(name,
                corePoolSize,
                maximumPoolSize,
                keepAliveTimeInSec));
    }

    public static ThreadPoolExecutor getThreadPoolExecutor(String name,
                                                           int corePoolSize,
                                                           int maximumPoolSize,
                                                           long keepAliveTimeInSec) {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat(name)
                .setDaemon(true)
                .build();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(corePoolSize,
                maximumPoolSize,
                keepAliveTimeInSec,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(maximumPoolSize),
                threadFactory);
        executor.allowCoreThreadTimeOut(true);
        executor.setRejectedExecutionHandler((r, e) -> log.warn("RejectedExecutionHandler called"));
        return executor;
    }

    /**
     * Copied from org.apache.commons.lang3.StringUtils.capitalize.
     */
    public static String capitalize(final String str) {
        int strLen;
        if (str == null || (strLen = str.length()) == 0) {
            return str;
        }

        final int firstCodepoint = str.codePointAt(0);
        final int newCodePoint = Character.toTitleCase(firstCodepoint);
        if (firstCodepoint == newCodePoint) {
            // already capitalized
            return str;
        }

        final int[] newCodePoints = new int[strLen]; // cannot be longer than the char array
        int outOffset = 0;
        newCodePoints[outOffset++] = newCodePoint; // copy the first codepoint
        for (int inOffset = Character.charCount(firstCodepoint); inOffset < strLen; ) {
            final int codepoint = str.codePointAt(inOffset);
            newCodePoints[outOffset++] = codepoint; // copy the remaining ones
            inOffset += Character.charCount(codepoint);
        }
        return new String(newCodePoints, 0, outOffset);
    }
}
