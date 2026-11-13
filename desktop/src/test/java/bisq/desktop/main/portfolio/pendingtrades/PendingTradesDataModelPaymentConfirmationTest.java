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

package bisq.desktop.main.portfolio.pendingtrades;

import bisq.desktop.Navigation;
import bisq.desktop.main.overlays.notifications.NotificationCenter;
import bisq.desktop.main.overlays.windows.WalletPasswordWindow;

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.filter.FilterPolicyService;
import bisq.core.offer.OfferUtil;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.support.traderchat.TraderChatManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.BuyerAsTakerTrade;
import bisq.core.trade.model.bisq_v1.SellerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.BuyerProtocol;
import bisq.core.trade.protocol.bisq_v1.SellerProtocol;
import bisq.core.user.Preferences;
import bisq.core.util.coin.CoinFormatter;

import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingTradesDataModelPaymentConfirmationTest {
    private static final String SELECTION_CHANGED =
            "The selected trade changed before the confirmation. Please select the trade again.";

    private final TradeManager tradeManager = mock(TradeManager.class);
    private final PendingTradesDataModel dataModel = new PendingTradesDataModel(tradeManager,
            mock(BtcWalletService.class),
            mock(PubKeyRing.class),
            mock(MediationManager.class),
            mock(RefundManager.class),
            mock(TraderChatManager.class),
            mock(Preferences.class),
            mock(P2PService.class),
            mock(WalletsSetup.class),
            mock(AccountAgeWitnessService.class),
            mock(DaoFacade.class),
            mock(Navigation.class),
            mock(WalletPasswordWindow.class),
            mock(NotificationCenter.class),
            mock(OfferUtil.class),
            mock(FilterPolicyService.class),
            mock(CoinFormatter.class));

    @Test
    void refusesToConfirmPaymentStartedForTradeWithError() {
        BuyerAsTakerTrade trade = selectBuyerTrade(true);
        AtomicReference<String> errorMessage = new AtomicReference<>();

        dataModel.onPaymentStarted(trade, () -> {
        }, errorMessage::set);

        assertEquals("Cannot confirm payment started for a failed trade.", errorMessage.get());
        verify(tradeManager, never()).getTradeProtocol(any());
    }

    @Test
    void confirmsPaymentStartedForTradeWithoutError() {
        BuyerAsTakerTrade trade = selectBuyerTrade(false);
        BuyerProtocol protocol = mock(BuyerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(protocol);
        ResultHandler resultHandler = () -> {
        };
        ErrorMessageHandler errorMessageHandler = errorMessage -> {
        };

        dataModel.onPaymentStarted(trade, resultHandler, errorMessageHandler);

        verify(protocol).onPaymentStarted(resultHandler, errorMessageHandler);
    }

    // The selection can change while the confirmation dialog is open, so the confirmation acts only on the trade for
    // which the dialog was opened.
    @Test
    void refusesToConfirmPaymentStartedWhenTheSelectionChanged() {
        BuyerAsTakerTrade confirmedTrade = mock(BuyerAsTakerTrade.class);
        selectBuyerTrade(false);
        AtomicReference<String> errorMessage = new AtomicReference<>();

        dataModel.onPaymentStarted(confirmedTrade, () -> {
        }, errorMessage::set);

        assertEquals(SELECTION_CHANGED, errorMessage.get());
        verify(tradeManager, never()).getTradeProtocol(any());
    }

    @Test
    void confirmsPaymentReceivedForTheSelectedTrade() {
        SellerAsMakerTrade trade = selectSellerTrade();
        SellerProtocol protocol = mock(SellerProtocol.class);
        when(tradeManager.getTradeProtocol(trade)).thenReturn(protocol);
        ResultHandler resultHandler = () -> {
        };
        ErrorMessageHandler errorMessageHandler = errorMessage -> {
        };

        dataModel.onFiatPaymentReceived(trade, resultHandler, errorMessageHandler);

        verify(protocol).onPaymentReceived(resultHandler, errorMessageHandler);
    }

    // Confirming the receipt of the payment releases the payout, so it must not act on another trade than the one the
    // seller confirmed.
    @Test
    void refusesToConfirmPaymentReceivedWhenTheSelectionChanged() {
        SellerAsMakerTrade confirmedTrade = mock(SellerAsMakerTrade.class);
        selectSellerTrade();
        AtomicReference<String> errorMessage = new AtomicReference<>();

        dataModel.onFiatPaymentReceived(confirmedTrade, () -> {
        }, errorMessage::set);

        assertEquals(SELECTION_CHANGED, errorMessage.get());
        verify(tradeManager, never()).getTradeProtocol(any());
    }

    @Test
    void recordsAndPersistsTheReceiptDateOfTheConfirmedTrade() {
        SellerAsMakerTrade trade = selectSellerTrade();
        long before = System.currentTimeMillis();

        dataModel.setSellerConfirmedPaymentReceiptDate(trade);

        long after = System.currentTimeMillis();
        ArgumentCaptor<Long> receiptDate = ArgumentCaptor.forClass(Long.class);
        verify(trade).setSellerConfirmedPaymentReceiptDate(receiptDate.capture());
        assertTrue(receiptDate.getValue() >= before && receiptDate.getValue() <= after, receiptDate.getValue().toString());
        verify(tradeManager).requestPersistence();
    }

    @Test
    void recordsNoReceiptDateWhenTheSelectionChanged() {
        SellerAsMakerTrade selectedTrade = selectSellerTrade();
        SellerAsMakerTrade confirmedTrade = mock(SellerAsMakerTrade.class);

        dataModel.setSellerConfirmedPaymentReceiptDate(confirmedTrade);

        verify(selectedTrade, never()).setSellerConfirmedPaymentReceiptDate(anyLong());
        verify(confirmedTrade, never()).setSellerConfirmedPaymentReceiptDate(anyLong());
        verify(tradeManager, never()).requestPersistence();
    }

    // The payout delay counts from the first confirmation of the receipt, so a second confirmation must not move it.
    @Test
    void keepsAnEarlierReceiptDate() {
        SellerAsMakerTrade trade = selectSellerTrade();
        when(trade.getSellerConfirmedPaymentReceiptDate()).thenReturn(1_700_000_000_000L);

        dataModel.setSellerConfirmedPaymentReceiptDate(trade);

        verify(trade, never()).setSellerConfirmedPaymentReceiptDate(anyLong());
        verify(tradeManager, never()).requestPersistence();
    }

    private BuyerAsTakerTrade selectBuyerTrade(boolean hasFailed) {
        BuyerAsTakerTrade trade = mock(BuyerAsTakerTrade.class);
        when(trade.hasFailed()).thenReturn(hasFailed);
        select(trade);
        return trade;
    }

    private SellerAsMakerTrade selectSellerTrade() {
        SellerAsMakerTrade trade = mock(SellerAsMakerTrade.class);
        select(trade);
        return trade;
    }

    private void select(Trade trade) {
        dataModel.selectedItemProperty.set(new PendingTradesListItem(trade, mock(CoinFormatter.class)));
    }
}
