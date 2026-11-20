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

package bisq.core.api;

import bisq.core.api.exception.FailedPreconditionException;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.OfferUtil;
import bisq.core.offer.bisq_v1.TakeOfferModel;
import bisq.core.offer.bsq_swap.BsqSwapTakeOfferModel;
import bisq.core.payment.payload.PaymentAccountPayload;
import bisq.core.trade.ClosedTradableFormatter;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.bsq_swap.BsqSwapTradeManager;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.SellerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerProtocol;
import bisq.core.user.User;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreTradesServiceConfirmPermittedTest {
    private static final String TRADE_ID = "tradeId";

    private CoreTradesService coreTradesService;
    private TradeManager tradeManager;

    @BeforeEach
    void setUp() {
        tradeManager = mock(TradeManager.class);
        coreTradesService = new CoreTradesService(new CoreContext(),
                mock(CoreWalletsService.class),
                mock(BtcWalletService.class),
                mock(OfferUtil.class),
                mock(BsqSwapTradeManager.class),
                mock(ClosedTradableManager.class),
                mock(ClosedTradableFormatter.class),
                mock(FailedTradesManager.class),
                mock(TakeOfferModel.class),
                mock(BsqSwapTakeOfferModel.class),
                tradeManager,
                mock(TradeUtil.class),
                mock(User.class));
    }

    // confirmPermitted() is not stubbed: the real rule of the trade's class decides by the dispute state.
    private <T extends Trade> T tradeIn(Class<T> tradeClass, Trade.DisputeState disputeState) {
        T trade = mock(tradeClass);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.isDepositConfirmed()).thenReturn(true);
        // A valid contract, so on the buyer side only the dispute state can reject the call.
        Contract contract = mock(Contract.class);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(mock(PaymentAccountPayload.class));
        when(trade.getContract()).thenReturn(contract);
        when(trade.getDisputeState()).thenReturn(disputeState);
        when(trade.confirmPermitted()).thenCallRealMethod();
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(trade));
        return trade;
    }

    @Test
    void buyerIsRejectedWhileTheTradeIsUnderArbitration() {
        Trade trade = tradeIn(BuyerAsTakerTrade.class, Trade.DisputeState.REFUND_REQUESTED);
        BuyerProtocol buyerProtocol = mock(BuyerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(buyerProtocol);

        assertThrows(FailedPreconditionException.class,
                () -> coreTradesService.confirmPaymentStarted(TRADE_ID, "txId", "txKey"));

        verify(buyerProtocol, never()).onPaymentStarted(any(), any());
        verify(trade, never()).setCounterCurrencyTxId(any());
        verify(trade, never()).setCounterCurrencyExtraData(any());
    }

    @Test
    void buyerProceedsDuringMediation() {
        Trade trade = tradeIn(BuyerAsTakerTrade.class, Trade.DisputeState.MEDIATION_REQUESTED);
        BuyerProtocol buyerProtocol = mock(BuyerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(buyerProtocol);

        coreTradesService.confirmPaymentStarted(TRADE_ID, null, null);

        verify(buyerProtocol).onPaymentStarted(any(), any());
    }

    @Test
    void sellerIsRejectedDuringMediation() {
        Trade trade = tradeIn(SellerAsTakerTrade.class, Trade.DisputeState.MEDIATION_REQUESTED);
        SellerProtocol sellerProtocol = mock(SellerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(sellerProtocol);
        when(trade.isFiatSent()).thenReturn(true);

        assertThrows(FailedPreconditionException.class,
                () -> coreTradesService.confirmPaymentReceived(TRADE_ID));

        verify(sellerProtocol, never()).onPaymentReceived(any(), any());
    }

    @Test
    void sellerProceedsWithoutADispute() {
        Trade trade = tradeIn(SellerAsTakerTrade.class, Trade.DisputeState.NO_DISPUTE);
        SellerProtocol sellerProtocol = mock(SellerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(sellerProtocol);
        when(trade.isFiatSent()).thenReturn(true);

        coreTradesService.confirmPaymentReceived(TRADE_ID);

        verify(sellerProtocol).onPaymentReceived(any(), any());
    }
}
