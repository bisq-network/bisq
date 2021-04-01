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

package bisq.apitest.botsupport.shutdown;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import static bisq.apitest.botsupport.util.FileUtil.deleteFileIfExists;
import static java.util.concurrent.TimeUnit.MILLISECONDS;



import bisq.apitest.botsupport.BotThread;


@Slf4j
public class ManualShutdown {

    public static final String SHUTDOWN_FILENAME = "/tmp/bot-shutdown";

    private static final AtomicBoolean SHUTDOWN_CALLED = new AtomicBoolean(false);

    /**
     * Looks for a /tmp/bot-shutdown file and throws a BotShutdownException if found.
     *
     * Running '$ touch /tmp/bot-shutdown' could be used to trigger a scaffold teardown.
     *
     * This is much easier than manually shutdown down bisq apps & bitcoind.
     */
    public static void startShutdownTimer() {
        deleteStaleShutdownFile();

        BotThread.runPeriodically(() -> {
            File shutdownFile = new File(SHUTDOWN_FILENAME);
            if (shutdownFile.exists()) {
                log.warn("Caught manual shutdown signal: {} file exists.", SHUTDOWN_FILENAME);
                try {
                    deleteFileIfExists(shutdownFile);
                } catch (IOException ex) {
                    log.error("", ex);
                    throw new IllegalStateException(ex);
                }
                SHUTDOWN_CALLED.set(true);
            }
        }, 2000, MILLISECONDS);
    }

    public static boolean isShutdownCalled() {
        return SHUTDOWN_CALLED.get();
    }

    public static void checkIfShutdownCalled(String warning) throws ManualBotShutdownException {
        if (isShutdownCalled())
            throw new ManualBotShutdownException(warning);
    }

    public static void setShutdownCalled(boolean isShutdownCalled) {
        SHUTDOWN_CALLED.set(isShutdownCalled);
    }

    private static void deleteStaleShutdownFile() {
        try {
            deleteFileIfExists(new File(SHUTDOWN_FILENAME));
        } catch (IOException ex) {
            log.error("", ex);
            throw new IllegalStateException(ex);
        }
    }
}
