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

/**
 * Metric base class.
 * 
 * @author Florian Reimair
 */
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
    public void configure(final Properties properties) {
        System.out.println(this.getName() + " (re)loading config...");

        // only configure the Properties which belong to us
        final Properties myProperties = new Properties();
        properties.forEach((k, v) -> {
            String key = (String) k;
            if (key.startsWith(getName()))
                myProperties.put(key.substring(key.indexOf(".") + 1), v);
        });

        if (suspend && myProperties.getProperty("enabled", "false").equals("true")) {
            suspend = false;
            System.out.println(this.getName() + " got activated. Starting up.");
        }

        // do some checks
        if (myProperties.isEmpty() || !myProperties.getProperty("enabled", "false").equals("true")
                || !myProperties.containsKey(INTERVAL)) {
            suspend = true;

            // some informative log output
            if (myProperties.isEmpty())
                System.out.println(this.getName() + " is not configured at all. Will not run.");
            else if (!myProperties.getProperty("enabled", "false").equals("true"))
                System.out.println(this.getName() + " is deactivated. Will not run.");
            else if (!myProperties.containsKey(INTERVAL))
                System.out.println(this.getName() + " is missing mandatory '" + INTERVAL + "' property. Will not run.");
            else
                System.out.println(this.getName() + " is misconfigured. Will not run.");
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

                execute();
                try {
                    Thread.sleep(Long.parseLong(configuration.getProperty(INTERVAL)) * 1000);
                } catch (InterruptedException ignore) {
                    // TODO Auto-generated catch block
                }
            }
        }
        System.out.println(this.getName() + " shutdown");
    }

    /**
     * Gets scheduled repeatedly.
     */
    protected abstract void execute();

    /**
     * Initiate graceful shutdown of the Metric.
     */
    public void shutdown() {
        shutdown = true;

        // interrupt the timer immediately so we can swiftly shut down
        this.interrupt();
        System.out.println(this.getName() + " shutdown requested");
    }

}
