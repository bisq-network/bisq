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

import bisq.monitor.reporter.ConsoleReporter;

import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ExecutionException;

import org.junit.Assert;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Disabled
public class MonitorInfrastructureTests {

    /**
     * A dummy metric for development purposes.
     */
    public class Dummy extends Metric {

        public Dummy() {
            super(new ConsoleReporter());
        }

        public boolean active() {
            return enabled();
        }

        @Override
        protected void execute() {
            try {
                Thread.sleep(50000);

            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "no interval", "typo"})
    public void basicConfigurationError(String configuration) {
        HashMap<String, Properties> lut = new HashMap<>();
        lut.put("empty", new Properties());
        Properties noInterval = new Properties();
        noInterval.put("Dummy.enabled", "true");
        lut.put("no interval", noInterval);
        Properties typo = new Properties();
        typo.put("Dummy.enabled", "true");
        //noinspection SpellCheckingInspection
        typo.put("Dummy.run.inteval", "1");
        lut.put("typo", typo);

        Dummy DUT = new Dummy();
        DUT.configure(lut.get(configuration));
        Assert.assertFalse(DUT.active());
    }

    @Test
    public void basicConfigurationSuccess() throws Exception {
        Properties correct = new Properties();
        correct.put("Dummy.enabled", "true");
        correct.put("Dummy.run.interval", "1");

        Dummy DUT = new Dummy();
        DUT.configure(correct);
        Assert.assertTrue(DUT.active());

        // graceful shutdown
        Metric.haltAllMetrics();
    }

    @Test
    public void reloadConfig() throws InterruptedException, ExecutionException {
        // our dummy
        Dummy DUT = new Dummy();

        // a second dummy to run as well
        Dummy DUT2 = new Dummy();
        DUT2.setName("Dummy2");
        Properties dummy2Properties = new Properties();
        dummy2Properties.put("Dummy2.enabled", "true");
        dummy2Properties.put("Dummy2.run.interval", "1");
        DUT2.configure(dummy2Properties);

        // disable
        DUT.configure(new Properties());
        Assert.assertFalse(DUT.active());
        Assert.assertTrue(DUT2.active());

        // enable
        Properties properties = new Properties();
        properties.put("Dummy.enabled", "true");
        properties.put("Dummy.run.interval", "1");
        DUT.configure(properties);
        Assert.assertTrue(DUT.active());
        Assert.assertTrue(DUT2.active());

        // disable again
        DUT.configure(new Properties());
        Assert.assertFalse(DUT.active());
        Assert.assertTrue(DUT2.active());

        // enable again
        DUT.configure(properties);
        Assert.assertTrue(DUT.active());
        Assert.assertTrue(DUT2.active());

        // graceful shutdown
        Metric.haltAllMetrics();
    }

    @Test
    public void shutdown() {
        Dummy DUT = new Dummy();
        DUT.setName("Dummy");
        Properties dummyProperties = new Properties();
        dummyProperties.put("Dummy.enabled", "true");
        dummyProperties.put("Dummy.run.interval", "1");
        DUT.configure(dummyProperties);
        try {
            Thread.sleep(2000);
            Metric.haltAllMetrics();
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
