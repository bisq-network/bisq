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

package bisq.core.offer.bsq_swap;

import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterManager;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferBookService;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenBsqSwapOfferServiceTest {

    @Test
    void redoProofOfWorkDoesNotPublishAReplacementWhenTheRemovalFailed() {
        OpenOfferManager openOfferManager = mock(OpenOfferManager.class);
        FilterPolicyService filterPolicyService = mock(FilterPolicyService.class);
        OpenBsqSwapOfferService service = new OpenBsqSwapOfferService(openOfferManager,
                mock(BtcWalletService.class),
                mock(BsqWalletService.class),
                mock(FeeService.class),
                mock(P2PService.class),
                mock(DaoFacade.class),
                mock(OfferBookService.class),
                mock(OfferUtil.class),
                mock(FilterManager.class),
                filterPolicyService,
                mock(PubKeyRing.class));

        Offer offer = mock(Offer.class);
        OpenOffer openOffer = mock(OpenOffer.class);
        when(openOffer.getOffer()).thenReturn(offer);
        when(openOffer.getId()).thenReturn("offerId");
        // An invalid proof of work routes the activation into the redo, which replaces the
        // offer under a mutated id.
        when(filterPolicyService.isProofOfWorkValid(offer)).thenReturn(false);

        // The removal fails, e.g. before the P2P network is bootstrapped. The old offer then
        // stays persisted and alive on the network, so no mutated-id replacement may be
        // published next to it.
        doAnswer(invocation -> {
            ((ErrorMessageHandler) invocation.getArgument(2)).handleErrorMessage("not bootstrapped");
            return null;
        }).when(openOfferManager).removeOpenOffer(eq(openOffer), any(ResultHandler.class), any(ErrorMessageHandler.class));

        service.activateOpenOffer(openOffer, () -> {
        }, errorMessage -> {
        });

        verify(openOfferManager).removeOpenOffer(eq(openOffer), any(ResultHandler.class), any(ErrorMessageHandler.class));
        verify(openOfferManager, never()).maybeRepublishOffer(any());
        verify(openOfferManager, never()).addOpenBsqSwapOffer(any());
    }
}
