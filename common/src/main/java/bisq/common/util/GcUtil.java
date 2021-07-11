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

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class GcUtil {
    public static void autoReleaseMemory() {
        autoReleaseMemory(1000);
    }

    /**
     * @param trigger  Threshold for free memory in MB when we invoke the garbage collector
     */
    public static void autoReleaseMemory(long trigger) {
        UserThread.runPeriodically(() -> maybeReleaseMemory(trigger), 60);
    }

    public static void maybeReleaseMemory() {
        maybeReleaseMemory(3000);
    }

    /**
     * @param trigger  Threshold for free memory in MB when we invoke the garbage collector
     */
    public static void maybeReleaseMemory(long trigger) {
        long totalMemory = Runtime.getRuntime().totalMemory();
        if (totalMemory > trigger * 1024 * 1024) {
            log.info("Invoke garbage collector. Total memory: {} {} {}", Utilities.readableFileSize(totalMemory), totalMemory, trigger * 1024 * 1024);
            System.gc();
            log.info("Total memory after gc() call: {}", Utilities.readableFileSize(Runtime.getRuntime().totalMemory()));
        }
    }
}
