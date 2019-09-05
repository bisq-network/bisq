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

package bisq.core.arbitration;

import bisq.core.support.dispute.arbitration.arbitrator.Arbitrator;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorManager;
import bisq.core.support.dispute.arbitration.arbitrator.ArbitratorService;
import bisq.core.user.User;

import bisq.network.p2p.NodeAddress;

import java.util.ArrayList;

import org.junit.Test;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ArbitratorManagerTest {



    @Test
    public void testIsArbitratorAvailableForLanguage() {
        User user = mock(User.class);
        ArbitratorService arbitratorService = mock(ArbitratorService.class);

        ArbitratorManager manager = new ArbitratorManager(null, arbitratorService, user, null, false);

        ArrayList<String> languagesOne = new ArrayList<String>() {{
            add("en");
            add("de");
        }};

        ArrayList<String> languagesTwo = new ArrayList<String>() {{
            add("en");
            add("es");
        }};

        Arbitrator one = new Arbitrator(new NodeAddress("arbitrator:1"), null, null, null,
                languagesOne, 0L, null, "", null,
                null, null);

        Arbitrator two = new Arbitrator(new NodeAddress("arbitrator:2"), null, null, null,
                languagesTwo, 0L, null, "", null,
                null, null);

        manager.addDisputeAgent(one, () -> {
        }, errorMessage -> {
        });
        manager.addDisputeAgent(two, () -> {
        }, errorMessage -> {
        });

        assertTrue(manager.isAgentAvailableForLanguage("en"));
        assertFalse(manager.isAgentAvailableForLanguage("th"));
    }

    @Test
    public void testGetArbitratorLanguages() {
        User user = mock(User.class);
        ArbitratorService arbitratorService = mock(ArbitratorService.class);

        ArbitratorManager manager = new ArbitratorManager(null, arbitratorService, user, null, false);

        ArrayList<String> languagesOne = new ArrayList<String>() {{
            add("en");
            add("de");
        }};

        ArrayList<String> languagesTwo = new ArrayList<String>() {{
            add("en");
            add("es");
        }};

        Arbitrator one = new Arbitrator(new NodeAddress("arbitrator:1"), null, null, null,
                languagesOne, 0L, null, "", null,
                null, null);

        Arbitrator two = new Arbitrator(new NodeAddress("arbitrator:2"), null, null, null,
                languagesTwo, 0L, null, "", null,
                null, null);

        ArrayList<NodeAddress> nodeAddresses = new ArrayList<NodeAddress>() {{
            add(two.getNodeAddress());
        }};

        manager.addDisputeAgent(one, () -> {
        }, errorMessage -> {
        });
        manager.addDisputeAgent(two, () -> {
        }, errorMessage -> {
        });

        assertThat(manager.getDisputeAgentLanguages(nodeAddresses), containsInAnyOrder("en", "es"));
        assertThat(manager.getDisputeAgentLanguages(nodeAddresses), not(containsInAnyOrder("de")));
    }

}
