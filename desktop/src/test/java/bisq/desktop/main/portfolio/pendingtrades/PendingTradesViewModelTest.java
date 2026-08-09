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

import bisq.core.account.witness.AccountAgeWitnessService;
import bisq.core.dao.state.DaoStateService;
import bisq.core.filter.FilterPolicyService;
import bisq.core.network.MessageState;
import bisq.core.offer.OfferUtil;
import bisq.core.provider.mempool.FeeValidationStatus;
import bisq.core.provider.mempool.MempoolService;
import bisq.core.provider.mempool.TxValidator;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.bisq_v1.TradeUtil;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.user.DontShowAgainLookup;
import bisq.core.user.Preferences;
import bisq.core.user.User;
import bisq.core.util.coin.BsqFormatter;
import bisq.core.util.coin.CoinFormatter;
import bisq.core.util.validation.BtcAddressValidator;

import bisq.network.p2p.P2PService;

import bisq.common.ClockWatcher;

import javafx.beans.property.SimpleObjectProperty;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.Date;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PendingTradesViewModelTest {
    private static final String TRADE_ID = "tradeId";
    private static final String DEPOSIT_TX_ID = "depositTxId";

    private Trade trade;
    private MempoolService mempoolService;
    private PendingTradesViewModel viewModel;
    private int lookups;

    @BeforeEach
    void setUp() {
        Preferences preferences = mock(Preferences.class);
        when(preferences.showAgain(anyString())).thenReturn(false);
        DontShowAgainLookup.setPreferences(preferences);

        trade = mock(Trade.class);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getShortId()).thenReturn(TRADE_ID);
        when(trade.getDepositTxId()).thenReturn(DEPOSIT_TX_ID);
        when(trade.getDate()).thenReturn(Date.from(Instant.now().minus(48, ChronoUnit.HOURS)));
        when(trade.hasFailed()).thenReturn(false);
        when(trade.isDepositConfirmed()).thenReturn(false);
        when(trade.stateProperty()).thenReturn(new SimpleObjectProperty<>(Trade.State.PREPARATION));
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getPaymentStartedMessageStateProperty())
                .thenReturn(new SimpleObjectProperty<>(MessageState.UNDEFINED));
        when(trade.getProcessModel()).thenReturn(processModel);

        mempoolService = mock(MempoolService.class);
        viewModel = new PendingTradesViewModel(mock(PendingTradesDataModel.class),
                mock(CoinFormatter.class),
                mock(BsqFormatter.class),
                mock(BtcAddressValidator.class),
                mock(P2PService.class),
                mempoolService,
                mock(ClosedTradableManager.class),
                mock(OfferUtil.class),
                mock(TradeUtil.class),
                mock(AccountAgeWitnessService.class),
                mock(ClockWatcher.class),
                mock(Navigation.class),
                mock(User.class));
        viewModel.onSelectedItemChanged(new PendingTradesListItem(trade, mock(CoinFormatter.class)));
    }

    @Test
    void depositTxUnknownToAllProvidersIsForgottenOnceAProviderKnowsItAgain() {
        deliver(txUnknownToAllProviders());
        assertTrue(viewModel.isDepositTxProvenDead(trade));

        // The tx turns up at a provider again, still unconfirmed. It is not gone from the network,
        // so the trade must no longer be offered for moving to failed trades.
        deliver(txKnownButUnconfirmed());
        assertFalse(viewModel.isDepositTxProvenDead(trade));
    }

    @Test
    void unreachableLookupNeitherFlagsNorClearsTheTrade() {
        deliver(txLookupUnreachable());
        assertFalse(viewModel.isDepositTxProvenDead(trade));

        deliver(txUnknownToAllProviders());
        assertTrue(viewModel.isDepositTxProvenDead(trade));

        // Not being able to ask is not an answer, so it must not revoke the earlier verdict either.
        deliver(txLookupUnreachable());
        assertTrue(viewModel.isDepositTxProvenDead(trade));
    }

    // Each check runs its own lookup with its own result handler, so capture a fresh one every time.
    @SuppressWarnings("unchecked")
    private void deliver(TxValidator result) {
        viewModel.checkForTimeoutAtTradeStep1();
        ArgumentCaptor<Consumer<TxValidator>> captor = ArgumentCaptor.forClass(Consumer.class);
        verify(mempoolService, times(++lookups)).checkTxIsConfirmed(eq(DEPOSIT_TX_ID), captor.capture());
        captor.getAllValues().get(lookups - 1).accept(result);
    }

    private static TxValidator txUnknownToAllProviders() {
        return newTxValidator().endResult(FeeValidationStatus.NACK_BTC_TX_NOT_FOUND);
    }

    private static TxValidator txLookupUnreachable() {
        return newTxValidator().endResult(FeeValidationStatus.NACK_TX_LOOKUP_UNREACHABLE);
    }

    private static TxValidator txKnownButUnconfirmed() {
        TxValidator txValidator = newTxValidator();
        txValidator.setJsonTxt("{\"txid\":\"" + DEPOSIT_TX_ID + "\",\"status\":{\"confirmed\":false}}");
        return txValidator;
    }

    private static TxValidator newTxValidator() {
        return new TxValidator(mock(DaoStateService.class), DEPOSIT_TX_ID, mock(FilterPolicyService.class));
    }
}
