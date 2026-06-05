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

package bisq.core.provider;

import bisq.core.filter.DenyList;

import bisq.common.config.Config;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PriceFeedNodeAddressProviderTest {
    @Test
    void appliesDenyListPriceRelayNodesAtStartupAndAfterFilterClear() {
        Properties properties = new Properties();
        properties.setProperty("bannedPriceRelayNodes", "foo");
        DenyList denyList = DenyList.fromProperties(properties);

        PriceFeedNodeAddressProvider provider = new PriceFeedNodeAddressProvider(new Config(),
                List.of("foo.onion", "bar.onion"),
                false,
                denyList);

        assertEquals("http://bar.onion/", provider.getBaseUrl());
        assertEquals(List.of("foo"), provider.getBannedNodes());

        provider.applyBannedNodes(null);

        assertEquals("http://bar.onion/", provider.getBaseUrl());
        assertEquals(List.of("foo"), provider.getBannedNodes());
    }
}
