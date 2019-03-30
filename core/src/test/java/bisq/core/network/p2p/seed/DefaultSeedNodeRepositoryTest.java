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

package bisq.core.network.p2p.seed;

import bisq.core.app.BisqEnvironment;

import bisq.network.p2p.NodeAddress;

import org.springframework.core.env.PropertySource;

import org.junit.Assert;
import org.junit.Test;

public class DefaultSeedNodeRepositoryTest {

    @Test
    public void getSeedNodes() {
        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(new BisqEnvironment(new PropertySource.StubPropertySource("name")), null);
        Assert.assertFalse(DUT.getSeedNodeAddresses().isEmpty());
    }

    @Test
    public void manualSeedNodes() {
        String seed1 = "asdf:8001";
        String seed2 = "fdsa:6001";
        String seedNodes = seed1 + "," + seed2;
        DefaultSeedNodeRepository DUT = new DefaultSeedNodeRepository(new BisqEnvironment(new PropertySource.StubPropertySource("name")), seedNodes);
        Assert.assertFalse(DUT.getSeedNodeAddresses().isEmpty());
        Assert.assertEquals(2, DUT.getSeedNodeAddresses().size());
        Assert.assertTrue(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed1)));
        Assert.assertTrue(DUT.getSeedNodeAddresses().contains(new NodeAddress(seed2)));
    }
}
