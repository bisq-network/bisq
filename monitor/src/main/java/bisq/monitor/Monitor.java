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

import bisq.monitor.metric.MarketStats;
import bisq.monitor.metric.P2PMarketStats;
import bisq.monitor.metric.P2PNetworkLoad;
import bisq.monitor.metric.P2PRoundTripTime;
import bisq.monitor.metric.P2PSeedNodeSnapshot;
import bisq.monitor.metric.PriceNodeStats;
import bisq.monitor.metric.TorHiddenServiceStartupTime;
import bisq.monitor.metric.TorRoundTripTime;
import bisq.monitor.metric.TorStartupTime;
import bisq.monitor.reporter.ConsoleReporter;
import bisq.monitor.reporter.GraphiteReporter;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;

import java.io.File;
import java.io.FileInputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkNotNull;



import sun.misc.Signal;

/**
 * Monitor executable for the Bisq network.
 *
 * @author Florian Reimair
 */
@Slf4j
public class Monitor {

    public static final File TOR_WORKING_DIR = new File("monitor/work/monitor-tor");
    private static String[] args = {};

    public static void main(String[] args) throws Throwable {
        Monitor.args = args;
        new Monitor().start();
    }

    /**
     * A list of all active {@link Metric}s
     */
    private final List<Metric> metrics = new ArrayList<>();

    /**
     * Starts up all configured Metrics.
     *
     * @throws Throwable in case something goes wrong
     */
    private void start() throws Throwable {

        // start Tor
        Tor.setDefault(new NativeTor(TOR_WORKING_DIR, null, null, false));

        //noinspection deprecation,deprecation,deprecation,deprecation,deprecation,deprecation,deprecation,deprecation
        Capabilities.app.addAll(Capability.TRADE_STATISTICS,
                Capability.TRADE_STATISTICS_2,
                Capability.ACCOUNT_AGE_WITNESS,
                Capability.ACK_MSG,
                Capability.PROPOSAL,
                Capability.BLIND_VOTE,
                Capability.DAO_STATE,
                Capability.BUNDLE_OF_ENVELOPES,
                Capability.REFUND_AGENT,
                Capability.MEDIATION,
                Capability.TRADE_STATISTICS_3);

        // assemble Metrics
        // - create reporters
        Reporter graphiteReporter = new GraphiteReporter();

        // only use ConsoleReporter if requested (for debugging for example)
        Properties properties = getProperties();
        if ("true".equals(properties.getProperty("System.useConsoleReporter", "false")))
            graphiteReporter = new ConsoleReporter();

        // - add available metrics with their reporters
        metrics.add(new TorStartupTime(graphiteReporter));
        metrics.add(new TorRoundTripTime(graphiteReporter));
        metrics.add(new TorHiddenServiceStartupTime(graphiteReporter));
        metrics.add(new P2PRoundTripTime(graphiteReporter));
        metrics.add(new P2PNetworkLoad(graphiteReporter));
        metrics.add(new P2PSeedNodeSnapshot(graphiteReporter));
        metrics.add(new P2PMarketStats(graphiteReporter));
        metrics.add(new PriceNodeStats(graphiteReporter));
        metrics.add(new MarketStats(graphiteReporter));

        // prepare configuration reload
        // Note that this is most likely only work on Linux
        Signal.handle(new Signal("USR1"), signal -> {
            try {
                configure();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        });

        // configure Metrics
        // - which also starts the metrics if appropriate
        configure();

        // exit Metrics gracefully on shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    // set the name of the Thread for debugging purposes
                    log.info("system shutdown initiated");

                    log.info("shutting down active metrics...");
                    Metric.haltAllMetrics();

                    try {
                        log.info("shutting down tor...");
                        Tor tor = Tor.getDefault();
                        checkNotNull(tor, "tor must not be null");
                        tor.shutdown();
                    } catch (Throwable ignore) {
                    }

                    log.info("system halt");
                }, "Monitor Shutdown Hook ")
        );
    }

    /**
     * Reload the configuration from disk.
     *
     * @throws Exception if something goes wrong
     */
    private void configure() throws Exception {
        Properties properties = getProperties();
        for (Metric current : metrics)
            current.configure(properties);
    }

    /**
     * Overloads a default set of properties with a file if given
     *
     * @return a set of properties
     * @throws Exception in case something goes wrong
     */
    private Properties getProperties() throws Exception {
        Properties result = new Properties();

        // if we have a config file load the config file, else, load the default config
        // from the resources
        if (args.length > 0)
            result.load(new FileInputStream(args[0]));
        else
            result.load(Monitor.class.getClassLoader().getResourceAsStream("metrics.properties"));

        return result;
    }
}
