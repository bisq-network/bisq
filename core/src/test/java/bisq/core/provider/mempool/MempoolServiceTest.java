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
import bisq.network.http.HttpException;

import bisq.common.config.Config;

import java.io.IOException;

import java.net.SocketTimeoutException;

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

    @Test
    void isTxUnknownResponseDetectsWrapped404() {
        // The shape the http client actually produces: HttpException rewrapped into an IOException.
        Throwable wrapped = new IOException("Direct request failed", new HttpException("Transaction not found", 404));

        assertTrue(MempoolService.isTxUnknownResponse(wrapped));
    }

    @Test
    void isTxUnknownResponseDetects404AtAnyDepth() {
        Throwable nested = new RuntimeException("outer",
                new IOException("middle", new HttpException("Transaction not found", 404)));

        assertTrue(MempoolService.isTxUnknownResponse(nested));
    }

    @Test
    void isTxUnknownResponseRejectsTransportFailure() {
        // No HttpException in the chain means we never got an answer, so the tx state stays unknown.
        Throwable connectionFailure = new IOException("Request via SOCKS proxy failed", new SocketTimeoutException());

        assertFalse(MempoolService.isTxUnknownResponse(connectionFailure));
    }

    @Test
    void isTxUnknownResponseRejectsOtherResponseCodes() {
        Throwable serverError = new IOException("Request failed", new HttpException("Request failed", 500));

        assertFalse(MempoolService.isTxUnknownResponse(serverError));
    }

    @Test
    void isTxUnknownResponseHandlesNull() {
        assertFalse(MempoolService.isTxUnknownResponse(null));
    }

    @Test
    void isTxUnknownResponseTerminatesOnCyclicCauseChain() {
        // A cyclic chain must not spin the calling thread, and must not be read as a 404.
        Throwable first = new IOException("first");
        Throwable second = new IOException("second", first);
        first.initCause(second);

        assertFalse(MempoolService.isTxUnknownResponse(first));
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
