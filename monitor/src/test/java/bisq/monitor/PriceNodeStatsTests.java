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

import bisq.monitor.metric.PriceNodeStats;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import java.io.File;

import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Florian Reimair
 */
@Disabled
public class PriceNodeStatsTests {

    private final static File torWorkingDirectory = new File("monitor/" + PriceNodeStatsTests.class.getSimpleName());

    /**
     * A dummy Reporter for development purposes.
     */
    private class DummyReporter extends Reporter {

        private Map<String, String> results;

        @Override
        public void report(long value) {
            Assert.fail();
        }

        public Map<String, String> results() {
            return results;
        }

        @Override
        public void report(Map<String, String> values) {
            results = values;
        }

        @Override
        public void report(Map<String, String> values, String prefix) {
            report(values);
        }

        @Override
        public void report(String key, String value, String timestamp, String prefix) {

        }

        @Override
        public void report(long value, String prefix) {
            report(value);
        }
    }

    @BeforeAll
    public static void setup() throws TorCtlException {
        // simulate the tor instance available to all metrics
        Tor.setDefault(new NativeTor(torWorkingDirectory));
    }

    @Test
    public void connect() {
        DummyReporter reporter = new DummyReporter();
        Metric DUT = new PriceNodeStats(reporter);


        Properties configuration = new Properties();
        configuration.put("PriceNodeStats.run.hosts", "http://5bmpx76qllutpcyp.onion");

        DUT.configure(configuration);

        DUT.execute();

        Assert.assertNotNull(reporter.results());
        Assert.assertTrue(reporter.results.size() > 0);
    }

    @AfterAll
    public static void cleanup() {
        Tor tor = Tor.getDefault();
        checkNotNull(tor, "tor must not be null");
        tor.shutdown();
        torWorkingDirectory.delete();
    }

}
