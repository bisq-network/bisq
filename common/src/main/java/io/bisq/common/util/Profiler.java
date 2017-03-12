/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Profiler {
    private static final Logger log = LoggerFactory.getLogger(Profiler.class);

    public static void printSystemLoad(Logger log) {
        log.info(printSystemLoadString());
    }

    public static String printSystemLoadString() {
        return "System load: Memory (MB)): " + getUsedMemoryInMB() + " / No. of threads: " + Thread.activeCount();
    }

    public static long getUsedMemoryInMB() {
        Runtime runtime = Runtime.getRuntime();
        long free = runtime.freeMemory() / 1024 / 1024;
        long total = runtime.totalMemory() / 1024 / 1024;
        return total - free;
    }

}
