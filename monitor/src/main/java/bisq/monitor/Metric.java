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

import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

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
    private volatile boolean shutdown = false;

    /**
     * our reporter
     */
    protected final Reporter reporter;
    private Thread thread = new Thread();

    /**
     * disable execution
     */
    private void disable() {
        shutdown = true;
    }

    /**
     * enable execution
     */
    private void enable() {
        shutdown = false;

        thread = new Thread(this);

        // set human readable name
        thread.setName(getName());

        // set as daemon, so that the jvm does not terminate the thread
        thread.setDaemon(true);

        thread.start();
    }

    /**
     * Constructor.
     */
    protected Metric(Reporter reporter) {

        this.reporter = reporter;

        setName(this.getClass().getSimpleName());

        // disable by default
        disable();
    }

    protected boolean enabled() {
        return !shutdown;
    }

    @Override
    public void configure(final Properties properties) {
        synchronized (this) {
            log.info("{} (re)loading config...", getName());
            super.configure(properties);
            reporter.configure(properties);

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
                    log.error("{} is misconfigured. Will not run.", getName());
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
        while (!shutdown) {
            // if not, execute all the things
            synchronized (this) {
                execute();
            }

            // make sure our configuration is not changed in the moment we want to query it
            String interval;
            synchronized (this) {
                interval = configuration.getProperty(INTERVAL);
            }

            // and go to sleep for the configured amount of time.
            try {
                Thread.sleep(Long.parseLong(interval) * 1000);
            } catch (InterruptedException ignore) {
            }
        }
        log.info("{} shutdown", getName());
    }

    /**
     * Gets scheduled repeatedly.
     */
    protected abstract void execute();

    /**
     * Initiate graceful shutdown of the Metric.
     */
    public void shutdown() {
        log.debug("{} shutdown requested", getName());
        shutdown = true;
    }

    protected void join() throws InterruptedException {
        thread.join();
    }

}
