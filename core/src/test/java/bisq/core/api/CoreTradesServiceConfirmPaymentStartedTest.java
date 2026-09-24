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
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;
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

class CoreTradesServiceConfirmPaymentStartedTest {
    private static final String TRADE_ID = "tradeId";

    private CoreTradesService coreTradesService;
    private Trade trade;
    private Contract contract;
    private BuyerProtocol buyerProtocol;

    @BeforeEach
    void setUp() {
        TradeManager tradeManager = mock(TradeManager.class);
        trade = mock(Trade.class);
        contract = mock(Contract.class);
        buyerProtocol = mock(BuyerProtocol.class);

        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.isDepositConfirmed()).thenReturn(true);
        when(tradeManager.getTradeById(TRADE_ID)).thenReturn(Optional.of(trade));
        when(tradeManager.getTradeProtocol(trade)).thenReturn(buyerProtocol);

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

    @Test
    void missingSellerPaymentAccountPayloadIsRejected() {
        when(trade.getContract()).thenReturn(contract);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(null);

        assertThrows(FailedPreconditionException.class,
                () -> coreTradesService.confirmPaymentStarted(TRADE_ID, "txId", "txKey"));
        verify(buyerProtocol, never()).onPaymentStarted(any(), any());
        verify(trade, never()).setCounterCurrencyTxId(any());
        verify(trade, never()).setCounterCurrencyExtraData(any());
    }

    @Test
    void missingContractIsRejected() {
        // The contract is set during the take offer handshake, so this is not the missing
        // message case; Trade.contract is @Nullable and the payload access would NPE.
        when(trade.getContract()).thenReturn(null);

        assertThrows(FailedPreconditionException.class,
                () -> coreTradesService.confirmPaymentStarted(TRADE_ID, null, null));
        verify(buyerProtocol, never()).onPaymentStarted(any(), any());
    }

    @Test
    void presentSellerPaymentAccountPayloadStartsPayment() {
        when(trade.getContract()).thenReturn(contract);
        when(contract.getSellerPaymentAccountPayload()).thenReturn(mock(PaymentAccountPayload.class));

        coreTradesService.confirmPaymentStarted(TRADE_ID, null, null);

        verify(buyerProtocol).onPaymentStarted(any(), any());
    }
}
