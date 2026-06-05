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

package bisq.core.filter;

import bisq.core.payment.payload.PaymentMethod;

import bisq.network.p2p.NodeAddress;

import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FilterPolicyServiceTests {
    @Test
    void bansWhenDenyListBansAndNoNetworkFilterBans() {
        Properties properties = new Properties();
        properties.setProperty("bannedCurrencies", "KYD");
        properties.setProperty("bannedPaymentMethods", "VENMO");
        properties.setProperty("nodeAddressesBannedFromTrading", "peer.onion:9999");
        DenyList denyList = DenyList.fromProperties(properties);
        FilterManager filterManager = mock(FilterManager.class);
        FilterPolicyService filterPolicyService = new FilterPolicyService(denyList, filterManager);

        assertTrue(filterPolicyService.isCurrencyBanned("KYD"));
        assertTrue(filterPolicyService.isPaymentMethodBanned(PaymentMethod.VENMO));
        assertTrue(filterPolicyService.isNodeAddressBanned(new NodeAddress("peer.onion:9999")));
    }

    @Test
    void bansWhenNetworkFilterBansAndDenyListDoesNot() {
        DenyList denyList = DenyList.empty();
        FilterManager filterManager = mock(FilterManager.class);
        when(filterManager.isOfferIdBanned("offer-id")).thenReturn(true);
        FilterPolicyService filterPolicyService = new FilterPolicyService(denyList, filterManager);

        assertTrue(filterPolicyService.isOfferIdBanned("offer-id"));
    }

    @Test
    void doesNotBanWhenNeitherSourceBans() {
        DenyList denyList = DenyList.empty();
        FilterPolicyService filterPolicyService = new FilterPolicyService(denyList, mock(FilterManager.class));

        assertFalse(filterPolicyService.isCurrencyBanned("USD"));
    }

    @Test
    void requiresUpdateWhenDenyListVersionIsNewer() {
        Properties properties = new Properties();
        properties.setProperty("requiredVersionForTrading", "99.0.0");
        DenyList denyList = DenyList.fromProperties(properties);
        FilterPolicyService filterPolicyService = new FilterPolicyService(denyList, mock(FilterManager.class));

        assertTrue(filterPolicyService.requireUpdateToNewVersionForTrading());
    }

    @Test
    void bansPaymentAccountDataHashFromDenyList() {
        Properties properties = new Properties();
        properties.setProperty("bannedPaymentAccountDataHashes", "abcdef");
        DenyList denyList = DenyList.fromProperties(properties);
        FilterPolicyService filterPolicyService = new FilterPolicyService(denyList, mock(FilterManager.class));

        assertTrue(filterPolicyService.isPaymentAccountDataHashBanned("abcdef"));
    }
}
