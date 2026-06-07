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

import org.bitcoinj.core.Coin;

import java.util.List;
import java.util.Properties;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void readsFeeValuesFromNetworkFilter() {
        Filter filter = mock(Filter.class);
        when(filter.getMakerFeeBtc()).thenReturn(1_000L);
        when(filter.getTakerFeeBtc()).thenReturn(2_000L);
        when(filter.getMakerFeeBsq()).thenReturn(3_000L);
        when(filter.getTakerFeeBsq()).thenReturn(4_000L);

        FilterManager filterManager = mock(FilterManager.class);
        when(filterManager.getFilter()).thenReturn(filter);
        FilterPolicyService filterPolicyService = new FilterPolicyService(DenyList.empty(), filterManager);

        assertEquals(Coin.valueOf(1_000L), filterPolicyService.getFeeFromFilter(true, true).orElseThrow());
        assertEquals(Coin.valueOf(2_000L), filterPolicyService.getFeeFromFilter(false, true).orElseThrow());
        assertEquals(Coin.valueOf(3_000L), filterPolicyService.getFeeFromFilter(true, false).orElseThrow());
        assertEquals(Coin.valueOf(4_000L), filterPolicyService.getFeeFromFilter(false, false).orElseThrow());
    }

    @Test
    void readsPowPolicyFromNetworkFilterAndManager() {
        Filter filter = mock(Filter.class);
        when(filter.isDisablePowMessage()).thenReturn(true);
        when(filter.getPowDifficulty()).thenReturn(512.0);

        FilterManager filterManager = mock(FilterManager.class);
        when(filterManager.getFilter()).thenReturn(filter);
        when(filterManager.getEnabledPowVersions()).thenReturn(List.of(1, 0));
        FilterPolicyService filterPolicyService = new FilterPolicyService(DenyList.empty(), filterManager);

        assertTrue(filterPolicyService.isPowMessageDisabled());
        assertEquals(512.0, filterPolicyService.getPowDifficulty(), 0.0);
        assertEquals(List.of(1, 0), filterPolicyService.getEnabledPowVersions());
    }
}
