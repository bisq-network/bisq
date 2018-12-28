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

import java.io.File;
import java.io.IOException;

import org.berndpruenster.netlayer.tor.HiddenServiceSocket;

import bisq.monitor.Metric;
import bisq.monitor.Reporter;
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

    public TorHiddenServiceStartupTime(Reporter reporter) throws IOException {
        super(reporter);
    }

    /**
     * synchronization helper. Required because directly closing the
     * HiddenServiceSocket in its ReadyListener causes a deadlock
     */
    private void await() {
        synchronized (hiddenServiceDirectory) {
            try {
                hiddenServiceDirectory.wait();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    private void proceed() {
        synchronized (hiddenServiceDirectory) {
            hiddenServiceDirectory.notify();
        }
    }

    @Override
    protected void execute() {
        // prepare settings. Fetch them everytime we run the Metric so we do not have to
        // restart on a config update
        int localPort = Integer.parseInt(configuration.getProperty(LOCAL_PORT, "9998"));
        int servicePort = Integer.parseInt(configuration.getProperty(SERVICE_PORT, "9999"));

        // clear directory so we get a new onion address everytime
        new File(hiddenServiceDirectory).delete();

        log.debug("creating the hidden service");
        // start timer - we do not need System.nanoTime as we expect our result to be in
        // the range of tenth of seconds.
        long start = System.currentTimeMillis();

        HiddenServiceSocket hiddenServiceSocket = new HiddenServiceSocket(localPort, hiddenServiceDirectory,
                servicePort);
        hiddenServiceSocket.addReadyListener(socket -> {
            // stop the timer and report
            reporter.report(System.currentTimeMillis() - start, "bisq." + getName());
            log.debug("the hidden service is ready");
            proceed();
            return null;
        });

        await();
        log.debug("going to unpublish the hidden service...");
        hiddenServiceSocket.close();
        log.debug("[going to unpublish the hidden service...] done");
    }
}
