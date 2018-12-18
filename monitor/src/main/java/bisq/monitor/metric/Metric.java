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
    protected Properties properties;

    /**
     * The properties of this very {@link Metric}
     */
    protected Properties configuration;
    protected boolean suspend = false;

    protected Metric() {
        // set human readable name
        super.setName(this.getClass().getSimpleName());

        // set as daemon, so that the jvm does not terminate the thread
        setDaemon(true);
    }

    /**
     * Configures the Metric.
     * 
     * @param properties
     */
    public synchronized void configure(final Properties properties) {
        log.info("{} (re)loading config...", getName());

        // only configure the Properties which belong to us
        final Properties myProperties = new Properties();
        properties.forEach((k, v) -> {
            String key = (String) k;
            if (key.startsWith(getName()))
                myProperties.put(key.substring(key.indexOf(".") + 1), v);
        });

        if (suspend && myProperties.getProperty("enabled", "false").equals("true")) {
            suspend = false;
            log.info("{} got activated. Starting up.", getName());
        }

        // do some checks
        if (myProperties.isEmpty() || !myProperties.getProperty("enabled", "false").equals("true")
                || !myProperties.containsKey(INTERVAL)) {
            suspend = true;

            // some informative log output
            if (myProperties.isEmpty())
                log.error("{} is not configured at all. Will not run.", getName());
            else if (!myProperties.getProperty("enabled", "false").equals("true"))
                log.info("{} is deactivated. Will not run.", getName());
            else if (!myProperties.containsKey(INTERVAL))
                log.error("{} is missing mandatory '" + INTERVAL + "' property. Will not run.", getName());
            else
                log.error("{} is misconfigured. Will not run.", getName());
        }

        interrupt();
        this.configuration = myProperties;
    }

    @Override
    public void run() {
        while (!shutdown) {
            synchronized (this) {
                while (suspend)
                    try {
                        wait();
                    } catch (InterruptedException ignore) {
                        // TODO Auto-generated catch block
                    }

                synchronized (this) {
                    execute();
                }

                try {
                    Thread.sleep(Long.parseLong(configuration.getProperty(INTERVAL)) * 1000);
                } catch (InterruptedException ignore) {
                    // TODO Auto-generated catch block
                }
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

        synchronized (this) {
            // interrupt the timer immediately so we can swiftly shut down
            this.interrupt();
        }
        log.debug("{} shutdown requested", getName());
    }

}
