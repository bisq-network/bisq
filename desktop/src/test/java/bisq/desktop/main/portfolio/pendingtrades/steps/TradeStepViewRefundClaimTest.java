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

import bisq.desktop.main.portfolio.pendingtrades.PendingTradesDataModel;
import bisq.desktop.main.portfolio.pendingtrades.PendingTradesViewModel;
import bisq.desktop.main.portfolio.pendingtrades.TradeStepInfo;

import bisq.core.support.dispute.Dispute;
import bisq.core.support.dispute.refund.RefundManager;
import bisq.core.trade.model.bisq_v1.Trade;

import javafx.event.ActionEvent;
import javafx.event.EventHandler;

import com.sun.javafx.tk.Toolkit;
import com.sun.javafx.perf.PerformanceTracker;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TradeStepViewRefundClaimTest {
    private MockedStatic<Toolkit> toolkit;

    @BeforeEach
    void setUp() {
        // No controls are constructed: only exercise view-state routing with a headless toolkit.
        toolkit = mockStatic(Toolkit.class);
        Toolkit headlessToolkit = mock(Toolkit.class);
        toolkit.when(Toolkit::getToolkit).thenReturn(headlessToolkit);
        when(headlessToolkit.getPerformanceTracker()).thenReturn(mock(PerformanceTracker.class));
    }

    @AfterEach
    void tearDown() {
        toolkit.close();
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void refundStateOffersSignedRequestAction(Trade.DisputeState state) throws Exception {
        Fixture fixture = new Fixture(state);
        fixture.view.updateDisputeState(state);
        fixture.verifySignedRequestAction();
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class, names = {"REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void mediationRefreshKeepsSignedRequestAction(Trade.DisputeState state) throws Exception {
        Fixture fixture = new Fixture(state);
        fixture.view.updateMediationResultState(true);
        fixture.verifySignedRequestAction();
    }

    private static class Fixture {
        final PendingTradesDataModel dataModel = mock(PendingTradesDataModel.class);
        final TradeStepInfo info = mock(TradeStepInfo.class);
        final TradeStepView view = mock(TradeStepView.class, CALLS_REAL_METHODS);
        final TradeStepInfo.State expectedState;

        Fixture(Trade.DisputeState state) throws Exception {
            Trade trade = mock(Trade.class);
            when(trade.getDisputeState()).thenReturn(state);
            when(trade.getId()).thenReturn("trade-id");
            RefundManager manager = mock(RefundManager.class);
            when(manager.findOwnDispute("trade-id")).thenReturn(Optional.of(mock(Dispute.class)));
            setField(dataModel, "refundManager", manager);
            PendingTradesViewModel model = mock(PendingTradesViewModel.class);
            setField(model, "dataModel", dataModel);
            setField(view, "model", model);
            setField(view, "trade", trade);
            setField(view, "tradeStepInfo", info);
            expectedState = state == Trade.DisputeState.REFUND_REQUESTED ?
                    TradeStepInfo.State.IN_REFUND_REQUEST_SELF_REQUESTED :
                    TradeStepInfo.State.IN_REFUND_REQUEST_PEER_REQUESTED;
        }

        @SuppressWarnings("unchecked")
        void verifySignedRequestAction() {
            ArgumentCaptor<EventHandler<ActionEvent>> action = ArgumentCaptor.forClass(EventHandler.class);
            verify(info).setState(expectedState);
            verify(info).setOnAction(action.capture());
            action.getValue().handle(new ActionEvent());
            verify(dataModel).onOpenDispute();
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
