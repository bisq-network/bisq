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

import bisq.monitor.metric.P2PRoundTripTime;
import bisq.monitor.reporter.ConsoleReporter;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;

import java.util.Map;
import java.util.Properties;

import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Test the round trip time metric against the hidden service of tor project.org.
 *
 * @author Florian Reimair
 */
@Disabled
class P2PRoundTripTimeTests {

    /**
     * A dummy Reporter for development purposes.
     */
    private class DummyReporter extends ConsoleReporter {

        private Map<String, String> results;

        @Override
        public void report(long value) {
            Assert.fail();
        }

        Map<String, String> hasResults() {
            return results;
        }

        @Override
        public void report(Map<String, String> values) {
            Assert.fail();
        }

        @Override
        public void report(long value, String prefix) {
            Assert.fail();
        }

        @Override
        public void report(Map<String, String> values, String prefix) {
            super.report(values, prefix);
            results = values;
        }
    }

    @BeforeAll
    static void setup() throws TorCtlException {
        // simulate the tor instance available to all metrics
        Tor.setDefault(new NativeTor(Monitor.TOR_WORKING_DIR));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "3", "4", "10"})
    void run(String sampleSize) throws Exception {
        DummyReporter reporter = new DummyReporter();

        // configure
        Properties configuration = new Properties();
        configuration.put("P2PRoundTripTime.enabled", "true");
        configuration.put("P2PRoundTripTime.run.interval", "2");
        if (!"default".equals(sampleSize))
            configuration.put("P2PRoundTripTime.run.sampleSize", sampleSize);
        // torproject.org hidden service
        configuration.put("P2PRoundTripTime.run.hosts", "http://fl3mmribyxgrv63c.onion:8000");
        configuration.put("P2PRoundTripTime.run.torProxyPort", "9052");

        Metric DUT = new P2PRoundTripTime(reporter);
        // start
        DUT.configure(configuration);

        // give it some time to start and then stop
        while (!DUT.enabled())
            Thread.sleep(2000);

        Metric.haltAllMetrics();

        // observe results
        Map<String, String> results = reporter.hasResults();
        Assert.assertFalse(results.isEmpty());
        Assert.assertEquals(results.get("sampleSize"), sampleSize.equals("default") ? "1" : sampleSize);

        Integer p25 = Integer.valueOf(results.get("p25"));
        Integer p50 = Integer.valueOf(results.get("p50"));
        Integer p75 = Integer.valueOf(results.get("p75"));
        Integer min = Integer.valueOf(results.get("min"));
        Integer max = Integer.valueOf(results.get("max"));
        Integer average = Integer.valueOf(results.get("average"));

        Assert.assertTrue(0 < min);
        Assert.assertTrue(min <= p25 && p25 <= p50);
        Assert.assertTrue(p50 <= p75);
        Assert.assertTrue(p75 <= max);
        Assert.assertTrue(min <= average && average <= max);
    }

    @AfterAll
    static void cleanup() {
        Tor tor = Tor.getDefault();
        checkNotNull(tor, "tor must not be null");
        tor.shutdown();
    }
}
