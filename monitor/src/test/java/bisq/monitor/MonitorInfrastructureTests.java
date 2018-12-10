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


import java.util.HashMap;
import java.util.Properties;

import org.junit.Assert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import bisq.monitor.metric.Metric;

public class MonitorInfrastructureTests {

    /**
     * A dummy metric for development purposes.
     */
    public class Dummy extends Metric {

        public Dummy() {
        }

        public boolean isSuspended() {
            return suspend;
        }

        @Override
        protected void execute() {
            System.out.println(this.getName() + " running");
        }

    }

    @ParameterizedTest
    @ValueSource(strings = { "empty", "no interval", "typo" })
    public void basicConfigurationError(String configuration) throws Exception {
        HashMap<String, Properties> lut = new HashMap<>();
        lut.put("empty", new Properties());
        Properties noInterval = new Properties();
        noInterval.put("Dummy.enabled", "true");
        lut.put("no interval", noInterval);
        Properties typo = new Properties();
        typo.put("Dummy.enabled", "true");
        typo.put("Dummy.run.inteval", "1");
        lut.put("typo", typo);

        Dummy DUT = new Dummy();
        DUT.configure(lut.get(configuration));
        DUT.start();
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.WAITING, DUT.getState());
    }

    @Test
    public void basicConfigurationSuccess() throws Exception {
        Properties correct = new Properties();
        correct.put("Dummy.enabled", "true");
        correct.put("Dummy.run.interval", "1");

        Dummy DUT = new Dummy();
        DUT.configure(correct);
        DUT.start();
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.TIMED_WAITING, DUT.getState());

        // graceful shutdown
        DUT.shutdown();
        DUT.join();
    }

    @Test
    public void reloadConfig() throws InterruptedException {
        // our dummy
        Dummy DUT = new Dummy();

        // disable
        DUT.configure(new Properties());
        DUT.start();
        // wait for the thread to be started
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.WAITING, DUT.getState());

        // enable
        Properties properties = new Properties();
        properties.put("Dummy.enabled", "true");
        properties.put("Dummy.run.interval", "1");
        DUT.configure(properties);
        // wait for things to be done
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.TIMED_WAITING, DUT.getState());

        // disable again
        DUT.configure(new Properties());
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.WAITING, DUT.getState());

        // enable again
        DUT.configure(properties);
        // wait for things to be done
        Thread.sleep(100);
        Assert.assertEquals(Thread.State.TIMED_WAITING, DUT.getState());

        // graceful shutdown
        DUT.shutdown();
        DUT.join();
    }
}
