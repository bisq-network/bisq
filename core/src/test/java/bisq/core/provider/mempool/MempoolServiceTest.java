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

package bisq.core.provider.mempool;

import bisq.core.dao.DaoFacade;
import bisq.core.dao.burningman.BurningManAddressList;
import bisq.core.dao.burningman.BurningManAddressListService;
import bisq.core.dao.burningman.BurningManPresentationService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.user.Preferences;

import bisq.network.Socks5ProxyProvider;

import bisq.common.config.Config;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MempoolServiceTest {
    private static final int LIST_VERSION = 1;
    private static final int SELECTION_HEIGHT = 767950;
    private static final String DONATION_ADDRESS = "donationAddress";
    private static final String LIST_LEGACY_ADDRESS = "listLegacyAddress";

    @Test
    void getAllBtcFeeReceiversIncludesLatestCurrentNetworkAddressListLegacyAddress() {
        MempoolService service = newService(addressList(Config.baseCurrencyNetwork().name()));

        List<String> receivers = service.getAllBtcFeeReceivers();

        assertTrue(receivers.contains(DONATION_ADDRESS));
        assertTrue(receivers.contains(LIST_LEGACY_ADDRESS));
    }

    @Test
    void getAllBtcFeeReceiversIgnoresNetworkMismatchedAddressListLegacyAddress() {
        MempoolService service = newService(addressList("OTHER_NETWORK"));

        List<String> receivers = service.getAllBtcFeeReceivers();

        assertTrue(receivers.contains(DONATION_ADDRESS));
        assertFalse(receivers.contains(LIST_LEGACY_ADDRESS));
    }

    private static MempoolService newService(BurningManAddressList addressList) {
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        when(filterPolicyService.getBtcFeeReceiverAddresses()).thenReturn(List.of());
        DaoFacade daoFacade = mock(DaoFacade.class);
        when(daoFacade.getAllDonationAddresses()).thenReturn(Set.of(DONATION_ADDRESS));
        BurningManAddressListService burningManAddressListService = mock(BurningManAddressListService.class);
        when(burningManAddressListService.getLatestVersion()).thenReturn(LIST_VERSION);
        when(burningManAddressListService.getAddressList(LIST_VERSION)).thenReturn(addressList);
        BurningManPresentationService burningManPresentationService = mock(BurningManPresentationService.class);
        when(burningManPresentationService.getBurningManCandidatesByName()).thenReturn(Map.of());

        return new MempoolService(mock(Socks5ProxyProvider.class),
                mock(Config.class),
                mock(Preferences.class),
                filterPolicyService,
                daoFacade,
                mock(DaoStateService.class),
                burningManAddressListService,
                burningManPresentationService);
    }

    private static BurningManAddressList addressList(String network) {
        return new BurningManAddressList(BurningManAddressList.SCHEMA_VERSION,
                LIST_VERSION,
                network,
                SELECTION_HEIGHT,
                SELECTION_HEIGHT,
                LIST_LEGACY_ADDRESS,
                List.of(new BurningManAddressList.Entry("allowedAddress", 1)));
    }
}
