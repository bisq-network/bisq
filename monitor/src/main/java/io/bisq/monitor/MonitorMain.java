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

package io.bisq.monitor;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.bisq.common.UserThread;
import io.bisq.common.util.Profiler;
import io.bisq.common.util.RestartUtil;
import io.bisq.common.util.Utilities;
import io.bisq.core.app.AppOptionKeys;
import io.bisq.core.app.BisqEnvironment;
import io.bisq.core.app.BisqExecutable;
import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.bitcoinj.store.BlockStoreException;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.google.common.base.Preconditions.checkNotNull;
import static io.bisq.core.app.BisqEnvironment.DEFAULT_APP_NAME;
import static io.bisq.core.app.BisqEnvironment.DEFAULT_USER_DATA_DIR;
import static spark.Spark.get;
import static spark.Spark.port;

@Slf4j
public class MonitorMain extends BisqExecutable {
    private static final long MAX_MEMORY_MB_DEFAULT = 1024;
    private static final long CHECK_MEMORY_PERIOD_SEC = 5 * 60;
    private static Monitor seedNodeMonitor;
    private volatile boolean stopped;
    private static long maxMemory = MAX_MEMORY_MB_DEFAULT;

    public static String monitorResult = "No data available yet";

    static {
        // Need to set default locale initially otherwise we get problems at non-english OS
        Locale.setDefault(new Locale("en", Locale.getDefault().getCountry()));

        Utilities.removeCryptographyRestrictions();
    }

    public static void main(String[] args) throws Exception {
        final ThreadFactory threadFactory = new ThreadFactoryBuilder()
                .setNameFormat("SeedNodeMonitorMain")
                .setDaemon(true)
                .build();
        UserThread.setExecutor(Executors.newSingleThreadExecutor(threadFactory));

        // We don't want to do the full argument parsing here as that might easily change in update versions
        // So we only handle the absolute minimum which is APP_NAME, APP_DATA_DIR_KEY and USER_DATA_DIR
        BisqEnvironment.setDefaultAppName("bisq_seednode_monitor");
        OptionParser parser = new OptionParser();
        parser.allowsUnrecognizedOptions();
        parser.accepts(AppOptionKeys.USER_DATA_DIR_KEY, description("User data directory", DEFAULT_USER_DATA_DIR))
                .withRequiredArg();
        parser.accepts(AppOptionKeys.APP_NAME_KEY, description("Application name", DEFAULT_APP_NAME))
                .withRequiredArg();

        OptionSet options;
        try {
            options = parser.parse(args);
        } catch (OptionException ex) {
            System.out.println("error: " + ex.getMessage());
            System.out.println();
            parser.printHelpOn(System.out);
            System.exit(EXIT_FAILURE);
            return;
        }
        MonitorEnvironment environment = getEnvironment(options);

        // need to call that before BisqAppMain().execute(args)
        BisqExecutable.initAppDir(environment.getProperty(AppOptionKeys.APP_DATA_DIR_KEY));

        // For some reason the JavaFX launch process results in us losing the thread context class loader: reset it.
        // In order to work around a bug in JavaFX 8u25 and below, you must include the following code as the first line of your realMain method:
        Thread.currentThread().setContextClassLoader(MonitorMain.class.getClassLoader());

        port(80);
        get("/", (req, res) -> {
            log.info("Incoming request from: " + req.userAgent());
            return seedNodeMonitor.getMetricsModel().getResultAsHtml();
        });

        new MonitorMain().execute(args);
    }

    public static MonitorEnvironment getEnvironment(OptionSet options) {
        return new MonitorEnvironment(checkNotNull(options));
    }

    @Override
    protected void customizeOptionParsing(OptionParser parser) {
        super.customizeOptionParsing(parser);

        parser.accepts(MonitorOptionKeys.SLACK_URL_SEED_CHANNEL,
                description("Set slack secret for seed node monitor", ""))
                .withRequiredArg();
        parser.accepts(MonitorOptionKeys.SLACK_BTC_SEED_CHANNEL,
                description("Set slack secret for Btc node monitor", ""))
                .withRequiredArg();
        parser.accepts(MonitorOptionKeys.SLACK_PROVIDER_SEED_CHANNEL,
                description("Set slack secret for provider node monitor", ""))
                .withRequiredArg();
    }

    @SuppressWarnings("InfiniteLoopStatement")
    @Override
    protected void doExecute(OptionSet options) {
        final MonitorEnvironment environment = getEnvironment(options);
        Monitor.setEnvironment(environment);

        UserThread.execute(() -> {
            try {
                seedNodeMonitor = new Monitor();
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        Thread.UncaughtExceptionHandler handler = (thread, throwable) -> {
            if (throwable.getCause() != null && throwable.getCause().getCause() != null &&
                    throwable.getCause().getCause() instanceof BlockStoreException) {
                log.error(throwable.getMessage());
            } else {
                log.error("Uncaught Exception from thread " + Thread.currentThread().getName());
                log.error("throwableMessage= " + throwable.getMessage());
                log.error("throwableClass= " + throwable.getClass());
                log.error("Stack trace:\n" + ExceptionUtils.getStackTrace(throwable));
                throwable.printStackTrace();
                log.error("We shut down the app because an unhandled error occurred");
                // We don't use the restart as in case of OutOfMemory errors the restart might fail as well
                // The run loop will restart the node anyway...
                System.exit(EXIT_FAILURE);
            }
        };
        Thread.setDefaultUncaughtExceptionHandler(handler);
        Thread.currentThread().setUncaughtExceptionHandler(handler);

        String maxMemoryOption = environment.getProperty(AppOptionKeys.MAX_MEMORY);
        if (maxMemoryOption != null && !maxMemoryOption.isEmpty()) {
            try {
                maxMemory = Integer.parseInt(maxMemoryOption);
            } catch (Throwable t) {
                log.error(t.getMessage());
            }
        }

        UserThread.runPeriodically(() -> {
            Profiler.printSystemLoad(log);
            if (!stopped) {
                long usedMemoryInMB = Profiler.getUsedMemoryInMB();
                if (usedMemoryInMB > (maxMemory * 0.8)) {
                    log.warn("\n\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n" +
                                    "We are over our memory warn limit and call the GC. usedMemoryInMB: {}" +
                                    "\n%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%\n\n",
                            usedMemoryInMB);
                    System.gc();
                    Profiler.printSystemLoad(log);
                }

                UserThread.runAfter(() -> {
                    if (Profiler.getUsedMemoryInMB() > maxMemory)
                        restart(environment);
                }, 5);
            }
        }, CHECK_MEMORY_PERIOD_SEC);

        while (true) {
            try {
                Thread.sleep(Long.MAX_VALUE);
            } catch (InterruptedException ignore) {
            }
        }
    }

    private void restart(BisqEnvironment bisqEnvironment) {
        stopped = true;
        seedNodeMonitor.gracefulShutDown(() -> {
            //noinspection finally
            try {
                final String[] tokens = bisqEnvironment.getAppDataDir().split("_");
                String logPath = "error_" + (tokens.length > 1 ? tokens[tokens.length - 2] : "") + ".log";
                RestartUtil.restartApplication(logPath);
            } catch (IOException e) {
                log.error(e.toString());
                e.printStackTrace();
            } finally {
                log.warn("Shutdown complete");
                System.exit(0);
            }
        });
    }
}
