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

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import bisq.monitor.metric.Dummy;
import bisq.monitor.metric.Metric;

/**
 * Monitor executable for the Bisq network.
 * 
 * @author Florian Reimair
 */
public class Monitor {

    public static void main(String[] args) throws InterruptedException {
        new Monitor().start();
    }

    /**
     * A list of all active {@link Metric}s
     */
    private List<Metric> metrics = new ArrayList<>();

    /**
     * Starts up all configured Metrics.
     * 
     * @throws InterruptedException
     */
    private void start() throws InterruptedException {
        // assemble Metrics
        metrics.add(new Dummy(new Properties()));

        // fire up all Metrics
        for (Metric current : metrics)
            current.start();

        // exit Metrics gracefully on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                for (Metric current : metrics) {
                    current.shutdown();
                    try {
                        // we need to join each metric, as they probably need time to gracefully shut
                        // down
                        current.join();
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    System.out.println("system halt");
                }
            }
        });

        // prevent the main thread to terminate
        System.out.println("joining metrics...");
        for (Metric current : metrics)
            current.join();
    }
}
