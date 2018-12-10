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

    private volatile boolean shutdown = false;

    public Metric(Properties properties) {
        // set as daemon, so that the jvm does not terminate the thread
        setDaemon(true);
        configure(properties);
        super.setName(this.getClass().getSimpleName());
    }

    /**
     * Configures the Metric.
     * 
     * @param properties
     */
    public void configure(Properties properties) {

    }

    @Override
    public void run() {
        while (!shutdown) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            execute();
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
        System.out.println(this.getName() + " shutdown requested");
    }

}
