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

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import com.runjva.sourceforge.jsocks.protocol.Socks5Proxy;
import com.runjva.sourceforge.jsocks.protocol.SocksSocket;

import bisq.monitor.Metric;
import bisq.monitor.Reporter;

/**
 * A Metric to measure the round-trip time to the Bisq seednodes via plain tor.
 * 
 * @author Florian Reimair
 */
public class TorRoundtripTime extends Metric {

    private static final String SAMPLE_SIZE = "run.sampleSize";
    private static final String HOSTS = "run.hosts";

    public TorRoundtripTime(Reporter reporter) {
        super(reporter);
    }

    @Override
    protected void execute() {
        SocksSocket socket = null;
        try {
            // fetch proxy
            Socks5Proxy proxy = Tor.getDefault().getProxy();

            // for each configured host
            for (String current : configuration.getProperty(HOSTS, "").split(",")) {
                // parse Url
                URL tmp = new URL(current);

                List<Long> samples = new ArrayList<>();

                while (samples.size() < Integer.parseInt(configuration.getProperty(SAMPLE_SIZE, "1"))) {
                    // start timer - we do not need System.nanoTime as we expect our result to be in
                    // seconds time.
                    long start = System.currentTimeMillis();

                    // connect
                    socket = new SocksSocket(proxy, tmp.getHost(), tmp.getPort());

                    // by the time we get here, we are connected
                    samples.add(System.currentTimeMillis() - start);

                    // cleanup
                    socket.close();
                }

                // aftermath
                Collections.sort(samples);

                // - average, max, min , samplesize
                LongSummaryStatistics statistics = samples.stream().mapToLong(val -> val).summaryStatistics();

                Map<String, String> results = new HashMap<>();
                results.put("average", String.valueOf(Math.round(statistics.getAverage())));
                results.put("max", String.valueOf(statistics.getMax()));
                results.put("min", String.valueOf(statistics.getMin()));
                results.put("sampleSize", String.valueOf(statistics.getCount()));

                // - p25, median, p75
                Integer[] percentiles = new Integer[]{25, 50, 75};
                for(Integer percentile : percentiles) {
                    double rank = statistics.getCount() * percentile / 100;
                    Long percentileValue;
                    if (samples.size() <= rank + 1)
                        percentileValue = samples.get(samples.size() - 1);
                    else if (Math.floor(rank) == rank)
                        percentileValue = samples.get((int) rank);
                    else
                        percentileValue = Math.round(samples.get((int) Math.floor(rank))
                                + (samples.get((int) (Math.floor(rank) + 1)) - samples.get((int) Math.floor(rank)))
                                        / (rank - Math.floor(rank)));
                    results.put("p" + percentile, String.valueOf(percentileValue));
                }

                // report
                reporter.report(results, "bisq." + getName());
            }
        } catch (TorCtlException | IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
