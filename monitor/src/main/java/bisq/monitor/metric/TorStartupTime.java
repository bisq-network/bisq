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
import java.util.LinkedHashMap;
import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.berndpruenster.netlayer.tor.Torrc;

/**
 * A Metric to measure the deployment and startup time of the packaged Tor
 * binaries. The data offered to the Prometheus server reflects the last run.
 * <p>
 * Note that scraping this very metric more often than the interval value
 * results in equal values which in turn affects subsequent calculations like
 * average. However, we consider this to be good enough - and as long we are
 * committed to use Prometheus, we really do not have a choice.
 * 
 * @author Florian Reimair
 */
public class TorStartupTime extends Metric {

    private final File torWorkingDirectory = new File("metric_torStartupTime");
    private final Torrc torOverrides;

    public TorStartupTime() throws IOException {
        super();

        LinkedHashMap<String, String> overrides = new LinkedHashMap<String, String>();
        overrides.put("SOCKSPort", "90501");

        torOverrides = new Torrc(overrides);
    }

    @Override
    protected void execute() {
        // cleanup installation
        torWorkingDirectory.delete();

        // start timer - we do not need System.nanoTime as we expect our result to be in
        // seconds time.
        long start = System.currentTimeMillis();

        try {
            Tor tor = new NativeTor(torWorkingDirectory, null, torOverrides);

            // stop the timer and set its timestamp
            report(System.currentTimeMillis() - start);

            // cleanup
            tor.shutdown();
        } catch (TorCtlException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
