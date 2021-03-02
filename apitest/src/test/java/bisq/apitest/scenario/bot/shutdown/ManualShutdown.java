package bisq.apitest.scenario.bot.shutdown;

import bisq.common.UserThread;

import java.io.File;
import java.io.IOException;

import java.util.concurrent.atomic.AtomicBoolean;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.file.FileUtil.deleteFileIfExists;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

@Slf4j
public class ManualShutdown {

    public static final String SHUTDOWN_FILENAME = "/tmp/bottest-shutdown";

    private static final AtomicBoolean SHUTDOWN_CALLED = new AtomicBoolean(false);

    /**
     * Looks for a /tmp/bottest-shutdown file and throws a BotShutdownException if found.
     *
     * Running '$ touch /tmp/bottest-shutdown' could be used to trigger a scaffold teardown.
     *
     * This is much easier than manually shutdown down bisq apps & bitcoind.
     */
    public static void startShutdownTimer() {
        deleteStaleShutdownFile();

        UserThread.runPeriodically(() -> {
            File shutdownFile = new File(SHUTDOWN_FILENAME);
            if (shutdownFile.exists()) {
                log.warn("Caught manual shutdown signal:  /tmp/bottest-shutdown file exists.");
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

    private static void deleteStaleShutdownFile() {
        try {
            deleteFileIfExists(new File(SHUTDOWN_FILENAME));
        } catch (IOException ex) {
            log.error("", ex);
            throw new IllegalStateException(ex);
        }
    }
}
