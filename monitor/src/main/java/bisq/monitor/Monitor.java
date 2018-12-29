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

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;

import bisq.monitor.metric.TorStartupTime;
import bisq.monitor.reporter.ConsoleReporter;
import bisq.monitor.reporter.GraphiteReporter;
import bisq.monitor.metric.TorRoundtripTime;
import bisq.monitor.metric.TorHiddenServiceStartupTime;
import lombok.extern.slf4j.Slf4j;
import sun.misc.Signal;

/**
 * Monitor executable for the Bisq network.
 * 
 * @author Florian Reimair
 */
@Slf4j
public class Monitor {

    private static String[] args = {};

    public static void main(String[] args) throws Throwable {
        Monitor.args = args;
        new Monitor().start();
    }

    /**
     * A list of all active {@link Metric}s
     */
    private List<Metric> metrics = new ArrayList<>();

    /**
     * Starts up all configured Metrics.
     * 
     * @throws Exception
     */
    private void start() throws Throwable {
        // start Tor
        Tor.setDefault(new NativeTor(new File("monitor-tor"), null, null, false));

        // assemble Metrics
        // - create reporters
//        ConsoleReporter consoleReporter = new ConsoleReporter();
        Reporter graphiteReporter = new GraphiteReporter();

        // - add available metrics with their reporters
        metrics.add(new TorStartupTime(graphiteReporter));
        metrics.add(new TorRoundtripTime(graphiteReporter));
        metrics.add(new TorHiddenServiceStartupTime(graphiteReporter));

        // prepare configuration reload
        // Note that this is most likely only work on Linux
        Signal.handle(new Signal("USR1"), signal -> {
            reload();
        });

        // configure Metrics
        // - which also starts the metrics if appropriate
        Properties properties = getProperties();
        for (Metric current : metrics)
            current.configure(properties);

        // exit Metrics gracefully on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                // set the name of the Thread for debugging purposes
                setName("shutdownHook");

                for (Metric current : metrics) {
                    current.shutdown();
                }

                // wait for the metrics to gracefully shut down
                for (Metric current : metrics)
                    try {
                        current.join();
                    } catch (InterruptedException ignore) {
                    }

                log.info("shutting down tor");
                Tor.getDefault().shutdown();

                log.info("system halt");
            }
        });

        // prevent the main thread to terminate
        log.info("joining metrics...");
        for (Metric current : metrics)
            current.join();
    }

    /**
     * Reload the configuration from disk.
     */
    private void reload() {
        try {
            Properties properties = getProperties();
            for (Metric current : metrics)
                current.configure(properties);
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

    /**
     * Overloads a default set of properties with a file if given
     * 
     * @return a set of properties
     * @throws Exception
     */
    private Properties getProperties() throws Exception {
        Properties defaults = new Properties();
        defaults.load(Monitor.class.getClassLoader().getResourceAsStream("metrics.properties"));

        Properties result = new Properties(defaults);

        if(args.length > 0)
            result.load(new FileInputStream(args[0]));

        return result;
    }
}
