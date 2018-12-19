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

package bisq.monitor.metric;

import java.util.Properties;
import java.util.concurrent.locks.ReentrantLock;

import lombok.extern.slf4j.Slf4j;

/**
 * Metric base class.
 * 
 * @author Florian Reimair
 */
@Slf4j
public abstract class Metric extends Thread {

    private static final String INTERVAL = "run.interval";
    private volatile boolean shutdown = false;

    /**
     * The properties of this very {@link Metric}
     */
    protected Properties configuration;

    /**
     * enable/disable helper
     */
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * disable execution
     */
    private void disable() {
        if (enabled())
            lock.lock();
    }

    /**
     * enable execution
     */
    private void enable() {
        if (!enabled())
            lock.unlock();
    }

    /**
     * @return true if execution is enabled
     */
    protected boolean enabled() {
        return !lock.isLocked();
    }

    /**
     * puts the Thread into a waiting position in case the Metric is disabled.
     * Blocking! Resumes execution if the Metric gets re-enabled.
     */
    private void waitIfDisabled() {
        // the thread gets into a waiting position until anyone unlocks the lock. If we
        // are suspended, we wait.
        lock.lock();
        // if execution gets resumed, we continue and readily release the lock as its
        // sole purpose is to control our execution
        lock.unlock();
    }

    /**
     * Constructor.
     */
    protected Metric() {
        // set human readable name
        super.setName(this.getClass().getSimpleName());

        // set as daemon, so that the jvm does not terminate the thread
        setDaemon(true);

        // disable by default
        disable();
    }

    /**
     * Configures the Metric.
     * 
     * @param properties
     */
    public void configure(final Properties properties) {
        synchronized (this) {
            log.info("{} (re)loading config...", getName());

            // only configure the Properties which belong to us
            final Properties myProperties = new Properties();
            properties.forEach((k, v) -> {
                String key = (String) k;
                if (key.startsWith(getName()))
                    myProperties.put(key.substring(key.indexOf(".") + 1), v);
            });

            // configure all properties that belong to us
            this.configuration = myProperties;

            // decide whether to enable or disable the task
            if (myProperties.isEmpty() || !myProperties.getProperty("enabled", "false").equals("true")
                    || !myProperties.containsKey(INTERVAL)) {
                disable();

                // some informative log output
                if (myProperties.isEmpty())
                    log.error("{} is not configured at all. Will not run.", getName());
                else if (!myProperties.getProperty("enabled", "false").equals("true"))
                    log.info("{} is deactivated. Will not run.", getName());
                else if (!myProperties.containsKey(INTERVAL))
                    log.error("{} is missing mandatory '" + INTERVAL + "' property. Will not run.", getName());
                else
                    log.error("{} is misconfigured. Will not run.", getName());
            } else if (!enabled() && myProperties.getProperty("enabled", "false").equals("true")) {
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
            waitIfDisabled();

            // if we get here after getting resumed we check for the shutdown condition
            if (shutdown)
                break;

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
     * Report our findings.
     * <p>
     * TODO atm we construct the report string to be used for graphite. We, of
     * course, need to send it to the graphite service eventually.
     * 
     * @param value
     */
    protected void report(long value) {
        String report = "bisq." + getName() + " " + value + " " + System.currentTimeMillis();
        System.err.println("Report: " + report);
    }

    /**
     * Initiate graceful shutdown of the Metric.
     */
    public void shutdown() {
        shutdown = true;

        // resume execution if suspended
        enable();

        log.debug("{} shutdown requested", getName());
    }

}
