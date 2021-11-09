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
import bisq.common.app.DevEnv;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcUtil {
    @Setter
    private static boolean DISABLE_GC_CALLS = false;
    private static int TRIGGER_MEM = 1000;
    private static int TRIGGER_MAX_MEM = 3000;

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
            long postGcMemory = Runtime.getRuntime().totalMemory();
            log.info("GC reduced memory by {}. Total memory before/after: {}/{}. Took {} ms.",
                    Utilities.readableFileSize(preGcMemory - postGcMemory),
                    Utilities.readableFileSize(preGcMemory),
                    Utilities.readableFileSize(postGcMemory),
                    System.currentTimeMillis() - ts);
            if (DevEnv.isDevMode()) {
                try {
                    // To see from where we got called
                    throw new RuntimeException("Dummy Exception for print stacktrace at maybeReleaseMemory");
                } catch (Throwable t) {
                    t.printStackTrace();
                }
            }
        }
    }
}
