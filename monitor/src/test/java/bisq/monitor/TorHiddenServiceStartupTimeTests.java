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

import bisq.monitor.metric.TorHiddenServiceStartupTime;

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

@Disabled // Ignore for normal test runs as the tests take lots of time
public class TorHiddenServiceStartupTimeTests {

    private final static File torWorkingDirectory = new File("monitor/" + TorHiddenServiceStartupTimeTests.class.getSimpleName());

    /**
     * A dummy Reporter for development purposes.
     */
    private class DummyReporter extends Reporter {

        private long result;

        @Override
        public void report(long value) {
            result = value;
        }

        public long results() {
            return result;
        }

        @Override
        public void report(Map<String, String> values) {
            report(Long.parseLong(values.values().iterator().next()));
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
    public void run() throws Exception {
        DummyReporter reporter = new DummyReporter();

        // configure
        Properties configuration = new Properties();
        configuration.put("TorHiddenServiceStartupTime.enabled", "true");
        configuration.put("TorHiddenServiceStartupTime.run.interval", "5");

        Metric DUT = new TorHiddenServiceStartupTime(reporter);
        // start
        DUT.configure(configuration);

        // give it some time and then stop
        Thread.sleep(180 * 1000);
        Metric.haltAllMetrics();

        // observe results
        Assert.assertTrue(reporter.results() > 0);
    }

    @AfterAll
    public static void cleanup() {
        Tor tor = Tor.getDefault();
        checkNotNull(tor, "tor must not be null");
        tor.shutdown();
        torWorkingDirectory.delete();
    }
}
