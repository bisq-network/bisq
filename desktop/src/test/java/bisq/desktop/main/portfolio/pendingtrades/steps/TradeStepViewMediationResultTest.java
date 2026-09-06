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

package bisq.desktop.main.portfolio.pendingtrades.steps;

import bisq.desktop.main.overlays.popups.Popup;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesDataModel;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.TradeStepInfo;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.locale.Res;
import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.dispute.mediation.MediationManager;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.util.coin.CoinFormatter;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import javafx.beans.property.SimpleObjectProperty;

import java.util.Optional;

import java.lang.reflect.Field;

import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;



import com.sun.javafx.perf.PerformanceTracker;
import com.sun.javafx.tk.Toolkit;

class TradeStepViewMediationResultTest {
    private MockedStatic<Toolkit> toolkit;
    private MockedConstruction<Popup> popups;
    private Fixture fixture;

    @BeforeEach
    void setUp() throws Exception {
        Res.setup();
        // Exercise the real view callbacks without constructing controls or displaying windows.
        toolkit = mockStatic(Toolkit.class);
        Toolkit headlessToolkit = mock(Toolkit.class);
        toolkit.when(Toolkit::getToolkit).thenReturn(headlessToolkit);
        when(headlessToolkit.getPerformanceTracker()).thenReturn(mock(PerformanceTracker.class));
        popups = mockConstruction(Popup.class, withSettings().defaultAnswer(RETURNS_SELF));
        fixture = new Fixture();
    }

    @AfterEach
    void tearDown() {
        popups.close();
        toolkit.close();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void unchangedResultAllowsAction(boolean accept) {
        action(openResult(), accept).run();

        if (accept) {
            verify(fixture.manager).onAcceptMediationResult(eq(fixture.trade), any(), any());
            verify(fixture.manager, never()).rejectMediationResult(any());
        } else {
            verify(fixture.manager).rejectMediationResult(fixture.trade);
            verify(fixture.manager, never()).onAcceptMediationResult(any(), any(), any());
        }
        assertEquals(1, popups.constructed().size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void replacementRefusesActionAndExplainsFailure(boolean accept) {
        Popup oldPopup = openResult();
        fixture.result.set(result(2_000));

        action(oldPopup, accept).run();

        verifyNoDecision();
        assertEquals(2, popups.constructed().size());
        verify(popups.constructed().get(1)).error(Res.get("portfolio.pending.mediationResult.error.resultChanged"));
        verify(popups.constructed().get(1)).show();
    }

    @Test
    void replacementWithIdenticalAmountsAlsoRequiresFreshReview() {
        Popup oldPopup = openResult();
        fixture.result.set(result(1_000));

        action(oldPopup, true).run();

        verifyNoDecision();
        assertEquals(2, popups.constructed().size());
    }

    @Test
    void errorDismissalDisplaysLatestResultAndOldCallbacksCannotAffectIt() {
        Popup oldPopup = openResult();
        Runnable oldAccept = action(oldPopup, true);
        Runnable oldReject = action(oldPopup, false);
        Runnable oldClose = close(oldPopup);
        fixture.result.set(result(2_000));
        oldAccept.run();
        Popup error = popups.constructed().get(1);

        oldAccept.run();
        oldReject.run();
        oldClose.run();
        fixture.view.updateMediationResultState(false);
        verifyNoDecision();
        assertEquals(2, popups.constructed().size());

        fixture.result.set(result(3_000));
        Runnable dismissError = close(error);
        dismissError.run();
        assertEquals(3, popups.constructed().size());
        Popup latestPopup = popups.constructed().get(2);
        verify(latestPopup).instruction(contains("3000 sat"));
        verify(latestPopup).instruction(contains("7000 sat"));

        oldAccept.run();
        oldReject.run();
        oldClose.run();
        dismissError.run();
        verifyNoDecision();
        assertEquals(3, popups.constructed().size());
        action(latestPopup, true).run();
        verify(fixture.manager).onAcceptMediationResult(eq(fixture.trade), any(), any());
    }

    @Test
    void anotherReplacementFailsAgainWithoutAutomaticallyApplyingEitherAction() {
        Popup firstPopup = openResult();
        fixture.result.set(result(2_000));
        action(firstPopup, true).run();
        close(popups.constructed().get(1)).run();
        Popup secondPopup = popups.constructed().get(2);

        fixture.result.set(result(3_000));
        action(secondPopup, false).run();

        verifyNoDecision();
        assertEquals(4, popups.constructed().size());
        verify(popups.constructed().get(3)).error(Res.get("portfolio.pending.mediationResult.error.resultChanged"));
    }

    @Test
    void leavingViewCancelsReopening() {
        Popup error = failStaleAction();

        fixture.view.deactivate();
        verify(error).hide();
        close(error).run();

        assertEquals(2, popups.constructed().size());
        verifyNoDecision();
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void arbitrationPreventsReopening(Trade.DisputeState state) {
        Popup error = failStaleAction();
        when(fixture.trade.getDisputeState()).thenReturn(state);

        close(error).run();

        assertEquals(2, popups.constructed().size());
        verifyNoDecision();
    }

    @Test
    void completedPayoutPreventsReopening() {
        Popup error = failStaleAction();
        when(fixture.trade.getPayoutTx()).thenReturn(mock(Transaction.class));

        close(error).run();

        assertEquals(2, popups.constructed().size());
        verifyNoDecision();
    }

    private Popup failStaleAction() {
        Popup oldPopup = openResult();
        fixture.result.set(result(2_000));
        action(oldPopup, true).run();
        return popups.constructed().get(1);
    }

    private Popup openResult() {
        fixture.view.updateMediationResultState(false);
        return popups.constructed().get(0);
    }

    private void verifyNoDecision() {
        verify(fixture.manager, never()).onAcceptMediationResult(any(), any(), any());
        verify(fixture.manager, never()).rejectMediationResult(any());
    }

    private static Runnable action(Popup popup, boolean accept) {
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        if (accept) {
            verify(popup).onAction(callback.capture());
        } else {
            verify(popup).onSecondaryAction(callback.capture());
        }
        return callback.getValue();
    }

    private static Runnable close(Popup popup) {
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(popup).onClose(callback.capture());
        return callback.getValue();
    }

    private static DisputeResult result(long buyerAmount) {
        DisputeResult result = new DisputeResult("trade-id", 1);
        result.setBuyerPayoutAmount(Coin.valueOf(buyerAmount));
        result.setSellerPayoutAmount(Coin.valueOf(10_000 - buyerAmount));
        return result;
    }

    private static class Fixture {
        final MediationManager manager = mock(MediationManager.class);
        final Trade trade = mock(Trade.class);
        final TradeStepView view = mock(TradeStepView.class, CALLS_REAL_METHODS);
        final SimpleObjectProperty<DisputeResult> result = new SimpleObjectProperty<>(result(1_000));

        Fixture() throws Exception {
            when(trade.getId()).thenReturn("trade-id");
            when(trade.getShortId()).thenReturn("trade-id");
            when(trade.getDisputeState()).thenReturn(Trade.DisputeState.MEDIATION_CLOSED);
            when(trade.getMediationResultState()).thenReturn(MediationResultState.UNDEFINED_MEDIATION_RESULT);
            ProcessModel processModel = mock(ProcessModel.class);
            when(trade.getProcessModel()).thenReturn(processModel);
            when(processModel.getTradePeer()).thenReturn(mock(TradingPeer.class));
            when(trade.getContract()).thenReturn(mock(Contract.class));
            when(trade.getDepositTx()).thenReturn(mock(Transaction.class));
            when(trade.getDelayedPayoutTx()).thenReturn(mock(Transaction.class));
            Dispute dispute = mock(Dispute.class);
            when(dispute.getDisputeResultProperty()).thenReturn(result);
            when(manager.findDispute("trade-id")).thenReturn(Optional.of(dispute));
            PendingTradesDataModel dataModel = mock(PendingTradesDataModel.class);
            setField(dataModel, "mediationManager", manager);
            setField(dataModel, "btcWalletService", mock(BtcWalletService.class));
            PendingTradesViewModel model = mock(PendingTradesViewModel.class);
            CoinFormatter formatter = mock(CoinFormatter.class);
            when(formatter.formatCoinWithCode(any())).thenAnswer(call -> ((Coin) call.getArgument(0)).value + " sat");
            setField(model, "btcFormatter", formatter);
            setField(model, "dataModel", dataModel);
            setField(view, "model", model);
            setField(view, "trade", trade);
            setField(view, "tradeStepInfo", mock(TradeStepInfo.class));
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        for (Class<?> type = target.getClass(); type != null; type = type.getSuperclass()) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                // Mockito subclasses and the view model inherit their state fields.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
