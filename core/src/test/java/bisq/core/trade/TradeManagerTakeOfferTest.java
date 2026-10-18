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

package bisq.core.trade;

import bisq.core.btc.TxFeeEstimationService;
import bisq.core.btc.wallet.BsqWalletService;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.burningman.DelayedPayoutTxReceiverService;
import bisq.core.dao.governance.param.Param;
import bisq.core.dao.governance.period.PeriodService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.locale.Res;
import bisq.core.offer.Offer;
import bisq.core.offer.OfferFilterService;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.OpenOfferManager;
import bisq.core.proto.persistable.CorePersistenceProtoResolver;
import bisq.core.provider.fee.FeeService;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.dispute.mediation.mediator.MediatorManager;
import bisq.core.trade.bisq_v1.DumpDelayedPayoutTx;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeResultHandler;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.Provider;
import bisq.core.trade.statistics.ReferralIdService;
import bisq.core.trade.statistics.TradeStatisticsManager;
import bisq.core.user.Preferences;
import bisq.core.user.User;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;
import bisq.common.crypto.KeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.persistence.PersistenceManager;

import org.bitcoinj.core.Coin;

import javax.annotation.Nullable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeManagerTakeOfferTest {
    private static final long TX_FEE_PER_VBYTE = 10;

    private FeeService feeService;
    private TradeManager tradeManager;
    private BtcWalletService btcWalletService;
    private Preferences preferences;
    private TxFeeEstimationService txFeeEstimationService;
    private Offer offer;
    private ErrorMessageHandler errorMessageHandler;

    @BeforeEach
    void setUp() {
        Res.setup();
        // FeeService keeps the DAO services in static fields that later tests read, so the mock
        // returns no DAO fee parameters, as before any FeeService exists
        DaoStateService daoStateService = mock(DaoStateService.class);
        when(daoStateService.getParamValueAsCoin(any(Param.class), anyInt())).thenReturn(Coin.ZERO);
        feeService = new FeeService(daoStateService, mock(PeriodService.class));
        feeService.updateFeeInfo(TX_FEE_PER_VBYTE, 1);
        Provider provider = mock(Provider.class);
        when(provider.getFeeService()).thenReturn(feeService);

        btcWalletService = mock(BtcWalletService.class);
        preferences = mock(Preferences.class);
        txFeeEstimationService = new TxFeeEstimationService(feeService, btcWalletService, preferences);

        tradeManager = spy(new TradeManager(mock(User.class), mock(KeyRing.class), btcWalletService,
                mock(BsqWalletService.class), mock(OpenOfferManager.class), mock(OfferFilterService.class),
                mock(ClosedTradableManager.class),
                mock(BsqSwapTradeManager.class), mock(FailedTradesManager.class), mock(P2PService.class),
                mock(PriceFeedService.class), mock(DelayedPayoutTxReceiverService.class),
                mock(TradeStatisticsManager.class), mock(TradeUtil.class), mock(MediatorManager.class),
                provider, mock(ClockWatcher.class), mock(PersistenceManager.class),
                mock(ReferralIdService.class), mock(CorePersistenceProtoResolver.class),
                mock(DumpDelayedPayoutTx.class), false));

        offer = mock(Offer.class);
        when(offer.getId()).thenReturn("offerId");
        doReturn(false).when(tradeManager).wasOfferAlreadyUsedInTrade("offerId");
        errorMessageHandler = mock(ErrorMessageHandler.class);
    }

    // The maker accepts up to 2 * 212 vbytes * fee rate. The taker averages its fee tx vsize
    // with the 233 vbytes deposit tx, so a fee tx from 617 vbytes on exceeds that.
    // The preference for the taker fee in BSQ adds 70 vbytes to the estimate; the check itself
    // does not depend on the fee currency.
    @ParameterizedTest
    @CsvSource({
            "175, true, true",
            "616, true, true",
            "617, true, false",
            "546, false, true",
            "547, false, false",
            "1400, true, false"
    })
    void takeOfferOnlyContactsMakerWithAcceptedTradeTxFee(int feeTxVsize,
                                                          boolean payFeeInBtc,
                                                          boolean acceptedByMaker) throws Exception {
        when(btcWalletService.getEstimatedFeeTxVsize(any(), any())).thenReturn(feeTxVsize);
        when(preferences.isPayFeeInBtc()).thenReturn(payFeeInBtc);
        Coin tradeTxFee = txFeeEstimationService.getEstimatedFeeAndTxVsizeForTaker(Coin.valueOf(1_000_000),
                Coin.valueOf(5_000)).first;

        takeOffer(tradeTxFee);

        assertRefusal(acceptedByMaker ? null : "takeOffer.failed.tradeTxFeeTooHigh");
    }

    // The fee is fixed when the screen shows it, so a later change of the fee rate can move it out
    // of the maker's range in both directions.
    @ParameterizedTest
    @CsvSource({
            "5, takeOffer.failed.tradeTxFeeTooHigh",
            "6, ",
            "21, ",
            "22, takeOffer.failed.tradeTxFeeTooLow"
    })
    void feeRateChangeAfterTheEstimateMovesTheTradeTxFeeOutOfTheRange(long feeRateAtTakeOffer,
                                                                      String refusalKey) throws Exception {
        when(btcWalletService.getEstimatedFeeTxVsize(any(), any())).thenReturn(175);
        when(preferences.isPayFeeInBtc()).thenReturn(true);
        Coin tradeTxFee = txFeeEstimationService.getEstimatedFeeAndTxVsizeForTaker(Coin.valueOf(1_000_000),
                Coin.valueOf(5_000)).first;
        assertEquals(2_330, tradeTxFee.value);
        feeService.updateFeeInfo(feeRateAtTakeOffer, 1);

        takeOffer(tradeTxFee);

        assertRefusal(refusalKey);
    }

    // The API states the fee that the maker expects, which is in the range unless it is below the
    // absolute minimum of 250 sat, at a fee rate of 1 sat/vbyte.
    @ParameterizedTest
    @CsvSource({
            "1, takeOffer.failed.tradeTxFeeTooLow",
            "2, ",
            "10, ",
            "100, ",
            "600, "
    })
    void apiTradeTxFeeIsInTheRange(long feeRate, String refusalKey) {
        feeService.updateFeeInfo(feeRate, 1);
        // As TakeOfferModel, which uses a fee tx vsize of 192 vbytes
        Coin apiTradeTxFee = new OfferUtil(null, null, null, null, null, null, null, null)
                .getTxFeeByVsize(feeService.getTxFeePerVbyte(), 192);

        takeOffer(apiTradeTxFee);

        assertRefusal(refusalKey);
    }

    // At 1 sat/vbyte the desktop states at least 233 sat, below the absolute minimum of 250 sat. That is
    // a problem of the fee rate, not of the wallet.
    @ParameterizedTest
    @CsvSource({"233", "249"})
    void tradeTxFeeBelowTheAbsoluteMinimumIsTooLow(long tradeTxFee) {
        feeService.updateFeeInfo(1, 1);

        takeOffer(Coin.valueOf(tradeTxFee));

        assertRefusal("takeOffer.failed.tradeTxFeeTooLow");
    }

    private void assertRefusal(@Nullable String refusalKey) {
        if (refusalKey == null) {
            verify(offer).checkOfferAvailability(any(), any(), any());
            verify(errorMessageHandler, never()).handleErrorMessage(anyString());
        } else {
            verify(errorMessageHandler).handleErrorMessage(Res.get(refusalKey));
            verify(offer, never()).checkOfferAvailability(any(), any(), any());
            assertTrue(tradeManager.getObservableList().isEmpty());
        }
    }

    @SuppressWarnings("unchecked")
    private void takeOffer(Coin tradeTxFee) {
        tradeManager.onTakeOffer(Coin.valueOf(1_000_000),
                tradeTxFee,
                Coin.valueOf(5_000),
                true,
                1,
                Coin.valueOf(1_200_000),
                offer,
                "paymentAccountId",
                true,
                false,
                mock(TradeResultHandler.class),
                errorMessageHandler);
    }
}
