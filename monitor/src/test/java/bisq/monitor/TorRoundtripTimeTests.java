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


import java.io.File;
import java.io.IOException;
import java.lang.Thread.State;
import java.util.Map;
import java.util.Properties;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import bisq.monitor.metric.TorRoundtripTime;

public class TorRoundtripTimeTests {

    private class Dut extends TorRoundtripTime {

        private Map<String, String> results;

        public Dut() throws IOException {
            super();
        }

        @Override
        protected void report(Map<String, String> values) {
            super.report(values);

            results = values;
        }

        public Map<String, String> hasResults() {
            return results;
        }
    }

    private static File workingDirectory = new File(TorRoundtripTimeTests.class.getSimpleName());

    @BeforeAll
    public static void setup() throws TorCtlException {
        Tor.setDefault(new NativeTor(workingDirectory));
    }

    @ParameterizedTest
    @ValueSource(strings = { "default", "3", "4", "10" })
    public void run(String sampleSize) throws Exception {

        // configure
        Properties configuration = new Properties();
        configuration.put("Dut.enabled", "true");
        configuration.put("Dut.run.interval", "2");
        if (!"default".equals(sampleSize))
            configuration.put("Dut.run.sampleSize", sampleSize);
        // torproject.org hidden service
        configuration.put("Dut.run.hosts", "http://expyuzz4wqqyqhjn.onion:80");

        Dut DUT = new Dut();
        DUT.configure(configuration);

        // start
        DUT.start();

        // give it some time to start and then stop
        while (DUT.getState() == State.NEW || DUT.getState() == State.RUNNABLE)
            Thread.sleep(10);

        DUT.shutdown();
        DUT.join();

        // observe results
        Map<String, String> results = DUT.hasResults();
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
    public static void cleanup() {
        Tor.getDefault().shutdown();
        workingDirectory.delete();
    }
}
