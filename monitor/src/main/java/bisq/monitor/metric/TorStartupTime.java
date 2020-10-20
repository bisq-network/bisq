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
import bisq.monitor.Reporter;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.Torrc;

import java.io.File;
import java.io.IOException;

import java.util.LinkedHashMap;
import java.util.Properties;

/**
 * A Metric to measure the deployment and startup time of the packaged Tor
 * binaries.
 *
 * @author Florian Reimair
 */
public class TorStartupTime extends Metric {

    private static final String SOCKS_PORT = "run.socksPort";
    private final File torWorkingDirectory = new File("monitor/work/metric_torStartupTime");
    private Torrc torOverrides;

    public TorStartupTime(Reporter reporter) {
        super(reporter);
    }

    @Override
    public void configure(Properties properties) {
        super.configure(properties);

        synchronized (this) {
            LinkedHashMap<String, String> overrides = new LinkedHashMap<>();
            overrides.put("SOCKSPort", configuration.getProperty(SOCKS_PORT, "90500"));

            try {
                torOverrides = new Torrc(overrides);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void execute() {
        // cleanup installation
        torWorkingDirectory.delete();
        Tor tor = null;
        // start timer - we do not need System.nanoTime as we expect our result to be in
        // tenth of seconds time.
        long start = System.currentTimeMillis();

        try {
            tor = new NativeTor(torWorkingDirectory, null, torOverrides);

            // stop the timer and set its timestamp
            reporter.report(System.currentTimeMillis() - start, getName());
        } catch (TorCtlException e) {
            e.printStackTrace();
        } finally {
            // cleanup
            if (tor != null)
                tor.shutdown();
        }
    }
}
