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
import bisq.monitor.OnionParser;
import bisq.monitor.Reporter;
import bisq.monitor.StatisticsHelper;

import bisq.network.p2p.NodeAddress;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import java.io.IOException;

import java.util.ArrayList;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * A Metric to measure the round-trip time to the Bisq seed nodes via plain tor.
 *
 * @author Florian Reimair
 */
public class TorRoundTripTime extends Metric {

    private static final String SAMPLE_SIZE = "run.sampleSize";
    private static final String HOSTS = "run.hosts";

    public TorRoundTripTime(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        SocksSocket socket;
        try {
            // fetch proxy
            Tor tor = Tor.getDefault();
            checkNotNull(tor, "tor must not be null");
            Socks5Proxy proxy = tor.getProxy();

            // for each configured host
            for (String current : configuration.getProperty(HOSTS, "").split(",")) {
                // parse Url
                NodeAddress tmp = OnionParser.getNodeAddress(current);

                List<Long> samples = new ArrayList<>();

                while (samples.size() < Integer.parseInt(configuration.getProperty(SAMPLE_SIZE, "1"))) {
                    // start timer - we do not need System.nanoTime as we expect our result to be in
                    // seconds time.
                    long start = System.currentTimeMillis();

                    // connect
                    socket = new SocksSocket(proxy, tmp.getHostName(), tmp.getPort());

                    // by the time we get here, we are connected
                    samples.add(System.currentTimeMillis() - start);

                    // cleanup
                    socket.close();
                }

                // report
                reporter.report(StatisticsHelper.process(samples), getName());
            }
        } catch (TorCtlException | IOException e) {
            e.printStackTrace();
        }
    }
}
