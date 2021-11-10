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

import bisq.common.UserThread;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcUtil {
    @Setter
    private static boolean DISABLE_GC_CALLS = false;
    private static final int TRIGGER_MEM = 1000;
    private static final int TRIGGER_MAX_MEM = 3000;
    private static int totalInvocations;
    private static long totalGCTime;

    public static void autoReleaseMemory() {
        if (DISABLE_GC_CALLS)
            return;

        autoReleaseMemory(TRIGGER_MEM);
    }

    public static void maybeReleaseMemory() {
        if (DISABLE_GC_CALLS)
            return;

        maybeReleaseMemory(TRIGGER_MAX_MEM);
    }

    /**
     * @param trigger  Threshold for free memory in MB when we invoke the garbage collector
     */
    private static void autoReleaseMemory(long trigger) {
        UserThread.runPeriodically(() -> maybeReleaseMemory(trigger), 120);
    }

    /**
     * @param trigger  Threshold for free memory in MB when we invoke the garbage collector
     */
    private static void maybeReleaseMemory(long trigger) {
        long ts = System.currentTimeMillis();
        long preGcMemory = Runtime.getRuntime().totalMemory();
        if (preGcMemory > trigger * 1024 * 1024) {
            System.gc();
            totalInvocations++;
            long postGcMemory = Runtime.getRuntime().totalMemory();
            long duration = System.currentTimeMillis() - ts;
            totalGCTime += duration;
            log.info("GC reduced memory by {}. Total memory before/after: {}/{}. Free memory: {}. Took {} ms. Total GC invocations: {} / Total GC time {} sec",
                    Utilities.readableFileSize(preGcMemory - postGcMemory),
                    Utilities.readableFileSize(preGcMemory),
                    Utilities.readableFileSize(postGcMemory),
                    Utilities.readableFileSize(Runtime.getRuntime().freeMemory()),
                    duration,
                    totalInvocations,
                    totalGCTime / 1000d);
           /* if (DevEnv.isDevMode()) {
                try {
                    // To see from where we got called
                    throw new RuntimeException("Dummy Exception for print stacktrace at maybeReleaseMemory");
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }*/
        }
    }
}
