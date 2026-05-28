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

package bisq.core.trade.bsq_swap;

import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterManager;
import bisq.core.offer.Offer;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapRequest;
import bisq.core.trade.protocol.bsq_swap.messages.SellersBsqSwapRequest;

import bisq.network.p2p.NodeAddress;

import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BsqSwapTakeOfferRequestVerificationTest {
    private static final int CHAIN_HEIGHT = 102;
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress PEER = new NodeAddress("peer.onion", 9999);
    private static final Coin TRADE_AMOUNT = Coin.COIN;
    private static final long MAKER_FEE = 50;
    private static final long TAKER_FEE = 150;
    // Derived from the network default so the assertions remain meaningful if the network
    // minimum is retuned.
    private static final long NETWORK_MIN = Config.baseCurrencyNetwork().getDefaultMinFeePerVbyte();
    // Strictly above 2 * NETWORK_MIN so the tolerance check rejects it.
    private static final long OUTSIDE_TOLERANCE = 2 * NETWORK_MIN + 1;

    @Test
    void acceptsRequestAtStoredMinimumFeeRate() {
        Fixture fixture = new Fixture(NETWORK_MIN);

        assertTrue(BsqSwapTakeOfferRequestVerification.isValid(fixture.openOfferManager,
                fixture.feeService,
                fixture.keyRing,
                PEER,
                newRequest(NETWORK_MIN)));
    }

    @Test
    void acceptsRequestBelowStoredMinimumFeeRateWhenToleranceAllowsIt() {
        // Force storedMin >= 2 so belowMin = storedMin - 1 is both strictly below the
        // stored minimum and still within the 2x tolerance window. This documents the
        // backward-compatible behavior for peers running versions before strict min-rate
        // enforcement.
        long storedMin = Math.max(2L, NETWORK_MIN);
        long belowMinWithinTolerance = storedMin - 1;
        Fixture fixture = new Fixture(storedMin);

        assertTrue(BsqSwapTakeOfferRequestVerification.isValid(fixture.openOfferManager,
                fixture.feeService,
                fixture.keyRing,
                PEER,
                newRequest(belowMinWithinTolerance)));
    }

    @Test
    void rejectsRequestWithBsqFeeBelowCurrentMinimumEvenWhenFeeToleranceWouldAllowIt() {
        Fixture fixture = new Fixture(NETWORK_MIN);

        assertFalse(BsqSwapTakeOfferRequestVerification.isValid(fixture.openOfferManager,
                fixture.feeService,
                fixture.keyRing,
                PEER,
                newRequest(NETWORK_MIN, MAKER_FEE - 1, TAKER_FEE)));
    }

    @Test
    void rejectsRequestOutsideFeeRateTolerance() {
        Fixture fixture = new Fixture(NETWORK_MIN);

        assertFalse(BsqSwapTakeOfferRequestVerification.isValid(fixture.openOfferManager,
                fixture.feeService,
                fixture.keyRing,
                PEER,
                newRequest(OUTSIDE_TOLERANCE)));
    }

    private static BsqSwapRequest newRequest(long txFeePerVbyte) {
        return newRequest(txFeePerVbyte, MAKER_FEE, TAKER_FEE);
    }

    private static BsqSwapRequest newRequest(long txFeePerVbyte, long makerFee, long takerFee) {
        return new SellersBsqSwapRequest(TRADE_ID,
                PEER,
                mock(PubKeyRing.class),
                TRADE_AMOUNT.value,
                txFeePerVbyte,
                makerFee,
                takerFee,
                System.currentTimeMillis());
    }

    private static class Fixture {
        private final FeeService feeService;
        private final KeyRing keyRing = mock(KeyRing.class);
        private final OpenOfferManager openOfferManager = mock(OpenOfferManager.class);

        private Fixture(long txFeePerVbyte) {
            feeService = configureFeeService(txFeePerVbyte);

            Offer offer = mock(Offer.class);
            when(offer.getId()).thenReturn(TRADE_ID);
            when(offer.isMyOffer(keyRing)).thenReturn(true);
            when(offer.getMinAmount()).thenReturn(Coin.valueOf(10_000));
            when(offer.getAmount()).thenReturn(TRADE_AMOUNT);

            OpenOffer openOffer = new OpenOffer(offer);
            when(openOfferManager.getOpenOfferById(TRADE_ID)).thenReturn(Optional.of(openOffer));
        }
    }

    private static FeeService configureFeeService(long txFeePerVbyte) {
        DaoStateService daoStateService = mock(DaoStateService.class);
        PeriodService periodService = mock(PeriodService.class);
        FilterManager filterManager = mock(FilterManager.class);
        when(periodService.getChainHeight()).thenReturn(CHAIN_HEIGHT);
        when(filterManager.getFilter()).thenReturn(null);

        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_MAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(MAKER_FEE));
        when(daoStateService.getParamValueAsCoin(Param.MIN_MAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(MAKER_FEE));
        when(daoStateService.getParamValueAsCoin(Param.DEFAULT_TAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TAKER_FEE));
        when(daoStateService.getParamValueAsCoin(Param.MIN_TAKER_FEE_BSQ, CHAIN_HEIGHT))
                .thenReturn(Coin.valueOf(TAKER_FEE));

        FeeService feeService = new FeeService(daoStateService, periodService);
        feeService.onAllServicesInitialized(filterManager);
        feeService.updateFeeInfo(txFeePerVbyte, txFeePerVbyte);
        return feeService;
    }
}
