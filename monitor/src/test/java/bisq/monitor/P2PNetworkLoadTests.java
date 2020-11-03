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

import bisq.monitor.metric.P2PNetworkLoad;
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
import org.junit.jupiter.api.Test;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Test the round trip time metric against the hidden service of tor project.org.
 *
 * @author Florian Reimair
 */
@Disabled
class P2PNetworkLoadTests {

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

    @Test
    void run() throws Exception {
        DummyReporter reporter = new DummyReporter();

        // configure
        Properties configuration = new Properties();
        configuration.put("P2PNetworkLoad.enabled", "true");
        configuration.put("P2PNetworkLoad.run.interval", "10");
        configuration.put("P2PNetworkLoad.run.hosts",
                "http://fl3mmribyxgrv63c.onion:8000, http://3f3cu2yw7u457ztq.onion:8000");

        Metric DUT = new P2PNetworkLoad(reporter);
        // start
        DUT.configure(configuration);

        // give it some time to start and then stop
        while (!DUT.enabled())
            Thread.sleep(500);
        Thread.sleep(20000);

        Metric.haltAllMetrics();

        // observe results
        Map<String, String> results = reporter.hasResults();
        Assert.assertFalse(results.isEmpty());
    }

    @AfterAll
    static void cleanup() {
        Tor tor = Tor.getDefault();
        checkNotNull(tor, "tor must not be null");
        tor.shutdown();
    }
}
