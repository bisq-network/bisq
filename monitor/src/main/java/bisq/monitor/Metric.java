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

package bisq.monitor;

import bisq.common.app.Version;
import bisq.common.util.Utilities;

import java.util.Properties;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

import static bisq.common.config.Config.BASE_CURRENCY_NETWORK;

/**
 * Starts a Metric (in its own {@link Thread}), manages its properties and shuts
 * it down gracefully. Furthermore, configuration updates and execution are done
 * in a thread-save manner. Implementing classes only have to implement the
 * {@link Metric#execute()} method.
 *
 * @author Florian Reimair
 */
@Slf4j
public abstract class Metric extends Configurable implements Runnable {

    private static final String INTERVAL = "run.interval";
    private static ScheduledExecutorService executor;
    protected final Reporter reporter;
    private ScheduledFuture<?> scheduler;

    /**
     * disable execution
     */
    private void disable() {
        if (scheduler != null)
            scheduler.cancel(false);
    }

    /**
     * enable execution
     */
    private void enable() {
        scheduler = executor.scheduleWithFixedDelay(this, new Random().nextInt(60),
                Long.parseLong(configuration.getProperty(INTERVAL)), TimeUnit.SECONDS);
    }

    /**
     * Constructor.
     */
    protected Metric(Reporter reporter) {

        this.reporter = reporter;

        setName(this.getClass().getSimpleName());

        if (executor == null) {
            executor = new ScheduledThreadPoolExecutor(6);
        }
    }

    boolean enabled() {
        if (scheduler != null)
            return !scheduler.isCancelled();
        else
            return false;
    }

    @Override
    public void configure(final Properties properties) {
        synchronized (this) {
            log.info("{} (re)loading config...", getName());
            super.configure(properties);
            reporter.configure(properties);

            Version.setBaseCryptoNetworkId(Integer.parseInt(properties.getProperty("System." + BASE_CURRENCY_NETWORK, "1"))); // defaults to BTC_TESTNET

            // decide whether to enable or disable the task
            if (configuration.isEmpty() || !configuration.getProperty("enabled", "false").equals("true")
                    || !configuration.containsKey(INTERVAL)) {
                disable();

                // some informative log output
                if (configuration.isEmpty())
                    log.error("{} is not configured at all. Will not run.", getName());
                else if (!configuration.getProperty("enabled", "false").equals("true"))
                    log.info("{} is deactivated. Will not run.", getName());
                else if (!configuration.containsKey(INTERVAL))
                    log.error("{} is missing mandatory '" + INTERVAL + "' property. Will not run.", getName());
                else
                    log.error("{} is mis-configured. Will not run.", getName());
            } else if (!enabled() && configuration.getProperty("enabled", "false").equals("true")) {
                // check if this Metric got activated after being disabled.
                // if so, resume execution
                enable();
                log.info("{} got activated. Starting up.", getName());
            }
        }
    }

    @Override
    public void run() {
        try {
            Thread.currentThread().setName("Metric: " + getName());

            // execute all the things
            synchronized (this) {
                log.info("{} started", getName());
                execute();
                log.info("{} done", getName());
            }
        } catch (Throwable e) {
            log.error("A metric misbehaved!", e);
        }
    }

    /**
     * Gets scheduled repeatedly.
     */
    protected abstract void execute();

    /**
     * initiate an orderly shutdown on all metrics. Blocks until all metrics are
     * shut down or after one minute.
     */
    public static void haltAllMetrics() {
        Utilities.shutdownAndAwaitTermination(executor, 2, TimeUnit.MINUTES);
    }
}
