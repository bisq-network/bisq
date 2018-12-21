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
import bisq.monitor.metric.Metric;
import bisq.monitor.metric.TorRoundtripTime;
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
        metrics.add(new TorStartupTime());
        metrics.add(new TorRoundtripTime());

        // configure Metrics
        Properties properties = getProperties();
        for (Metric current : metrics)
            current.configure(properties);

        // prepare configuration reload
        // Note that this is most likely only work on Linux
        Signal.handle(new Signal("USR1"), signal -> {
            reload();
        });

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
                    log.trace("system halt");
                }
            }
        });

        // prevent the main thread to terminate
        log.trace("joining metrics...");
        for (Metric current : metrics)
            current.join();

        log.info("shutting down tor");
        Tor.getDefault().shutdown();
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
