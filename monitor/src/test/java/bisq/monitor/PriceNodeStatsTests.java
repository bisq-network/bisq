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

import org.junit.Assert;
import org.junit.Test;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * @author Florian Reimair
 */
public class PriceNodeStatsTests {

    private final static File torWorkingDirectory = new File("monitor/" + PriceNodeStatsTests.class.getSimpleName());

    @BeforeAll
    public static void setup() throws TorCtlException {
        // simulate the tor instance available to all metrics
        Tor.setDefault(new NativeTor(torWorkingDirectory));
    }

    @AfterAll
    public static void cleanup() {
        Tor tor = Tor.getDefault();
        checkNotNull(tor, "tor must not be null");
        tor.shutdown();
        torWorkingDirectory.delete();
    }

}
