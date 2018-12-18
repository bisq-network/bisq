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
import java.util.Properties;

import org.berndpruenster.netlayer.tor.NativeTor;
import org.berndpruenster.netlayer.tor.Tor;
import org.berndpruenster.netlayer.tor.TorCtlException;
import org.junit.Assert;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import bisq.monitor.metric.TorRoundtripTime;

public class TorRoundtripTimeTests {

    private class Dut extends TorRoundtripTime {

        private long result;

        public Dut() throws IOException {
            super();
        }

        @Override
        protected void report(long value) {
            // TODO Auto-generated method stub
            super.report(value);

            result = value;
        }

        public long results() {
            return result;
        }
    }

    private static File workingDirectory = new File(TorRoundtripTimeTests.class.getSimpleName());

    @BeforeAll
    public static void setup() throws TorCtlException {
        Tor.setDefault(new NativeTor(workingDirectory));
    }

    @Test
    public void run() throws Exception {

        // configure
        Properties configuration = new Properties();
        configuration.put("Dut.enabled", "true");
        configuration.put("Dut.run.interval", "2");
        // torproject.org hidden service
        configuration.put("Dut.run.hosts", "http://expyuzz4wqqyqhjn.onion:80");

        Dut DUT = new Dut();
        DUT.configure(configuration);

        // start
        DUT.start();

        // give it some time and then stop
        Thread.sleep(15 * 1000);
        DUT.shutdown();

        // observe results
        Assert.assertTrue(DUT.results() > 0);
    }

    @AfterAll
    public static void cleanup() {
        Tor.getDefault().shutdown();
        workingDirectory.delete();
    }
}
