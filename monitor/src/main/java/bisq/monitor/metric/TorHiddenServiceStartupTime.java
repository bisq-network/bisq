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

import bisq.monitor.Metric;
import bisq.monitor.Monitor;
import bisq.monitor.Reporter;
import bisq.monitor.ThreadGate;

import org.berndpruenster.netlayer.tor.HiddenServiceSocket;

import java.io.File;

import lombok.extern.slf4j.Slf4j;

/**
 * A Metric to measure the startup time of a Tor Hidden Service on a already
 * running Tor.
 *
 * @author Florian Reimair
 */
@Slf4j
public class TorHiddenServiceStartupTime extends Metric {

    private static final String SERVICE_PORT = "run.servicePort";
    private static final String LOCAL_PORT = "run.localPort";
    private final String hiddenServiceDirectory = "metric_" + getName();
    private final ThreadGate gate = new ThreadGate();

    public TorHiddenServiceStartupTime(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        // prepare settings. Fetch them every time we run the Metric so we do not have to
        // restart on a config update
        int localPort = Integer.parseInt(configuration.getProperty(LOCAL_PORT, "9998"));
        int servicePort = Integer.parseInt(configuration.getProperty(SERVICE_PORT, "9999"));

        // clear directory so we get a new onion address every time
        new File(Monitor.TOR_WORKING_DIR + "/" + hiddenServiceDirectory).delete();

        log.debug("creating the hidden service");

        gate.engage();

        // start timer - we do not need System.nanoTime as we expect our result to be in
        // the range of tenth of seconds.
        long start = System.currentTimeMillis();

        HiddenServiceSocket hiddenServiceSocket = new HiddenServiceSocket(localPort, hiddenServiceDirectory,
                servicePort);
        hiddenServiceSocket.addReadyListener(socket -> {
            // stop the timer and report
            reporter.report(System.currentTimeMillis() - start, getName());
            log.debug("the hidden service is ready");
            gate.proceed();
            return null;
        });

        gate.await();
        log.debug("going to revoke the hidden service...");
        hiddenServiceSocket.close();
        log.debug("[going to revoke the hidden service...] done");
    }
}
