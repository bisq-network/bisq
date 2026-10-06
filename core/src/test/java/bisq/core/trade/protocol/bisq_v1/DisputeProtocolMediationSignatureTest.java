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

package bisq.core.trade.protocol.bisq_v1;

import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.TradeMessage;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxSignatureMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.ProcessMediatedPayoutSignatureMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The peer's signature of the mediated payout is checked against our mediation result. One that
 * arrives before the result is kept and handled once the result is applied (dispute state
 * MEDIATION_CLOSED), instead of being checked against payout amounts that are not set yet.
 */
class DisputeProtocolMediationSignatureTest {
    private static final String TRADE_ID = "trade-id";
    private static final NodeAddress PEER = new NodeAddress("peer.onion", 9999);

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class,
            names = {"NO_DISPUTE", "MEDIATION_REQUESTED", "MEDIATION_STARTED_BY_PEER"})
    void signatureBeforeResultIsHandledOnceResultIsApplied(Trade.DisputeState stateBeforeResult) {
        TestSetup setup = new TestSetup(stateBeforeResult);
        MediatedPayoutTxSignatureMessage message = signatureMessage("uid");

        setup.protocol.onMailboxMessage(message, PEER);
        assertTrue(setup.protocol.handledMessages.isEmpty());

        setup.disputeState.set(Trade.DisputeState.MEDIATION_CLOSED);
        assertEquals(List.of(message), setup.protocol.handledMessages);
        assertArrayEquals(new Class[]{ProcessMediatedPayoutSignatureMessage.class}, setup.protocol.selectedTasks);
    }

    @Test
    void directSignatureWithoutDisputeWaitsUntilResultIsApplied() {
        TestSetup setup = new TestSetup(Trade.DisputeState.NO_DISPUTE);
        MediatedPayoutTxSignatureMessage message = signatureMessage("uid");

        setup.protocol.onTradeMessage(message, PEER);
        setup.disputeState.set(Trade.DisputeState.MEDIATION_STARTED_BY_PEER);
        assertTrue(setup.protocol.handledMessages.isEmpty());

        setup.disputeState.set(Trade.DisputeState.MEDIATION_CLOSED);
        setup.disputeState.set(Trade.DisputeState.REFUND_REQUESTED);
        assertEquals(List.of(message), setup.protocol.handledMessages);
    }

    @ParameterizedTest
    @EnumSource(value = Trade.DisputeState.class,
            names = {"MEDIATION_CLOSED", "REFUND_REQUESTED", "REFUND_REQUEST_STARTED_BY_PEER"})
    void signatureAfterResultIsHandledAtOnce(Trade.DisputeState state) {
        TestSetup setup = new TestSetup(state);
        MediatedPayoutTxSignatureMessage message = signatureMessage("uid");

        setup.protocol.onTradeMessage(message, PEER);

        assertEquals(List.of(message), setup.protocol.handledMessages);
        assertArrayEquals(new Class[]{ProcessMediatedPayoutSignatureMessage.class}, setup.protocol.selectedTasks);
    }

    @Test
    void newerSignatureReplacesTheKeptOne() {
        TestSetup setup = new TestSetup(Trade.DisputeState.MEDIATION_STARTED_BY_PEER);
        MediatedPayoutTxSignatureMessage first = signatureMessage("first");
        MediatedPayoutTxSignatureMessage second = signatureMessage("second");

        setup.protocol.onMailboxMessage(first, PEER);
        setup.protocol.onTradeMessage(second, PEER);
        setup.disputeState.set(Trade.DisputeState.MEDIATION_CLOSED);

        assertEquals(List.of(second), setup.protocol.handledMessages);
    }

    private static MediatedPayoutTxSignatureMessage signatureMessage(String uid) {
        return new MediatedPayoutTxSignatureMessage(new byte[]{1}, TRADE_ID, PEER, uid);
    }

    private static final class TestSetup {
        private final SimpleObjectProperty<Trade.DisputeState> disputeState;
        private final TestDisputeProtocol protocol;

        private TestSetup(Trade.DisputeState initialDisputeState) {
            disputeState = new SimpleObjectProperty<>(initialDisputeState);
            ProcessModel processModel = new ProcessModel(TRADE_ID, "account-id", null);
            Trade trade = mock(Trade.class);
            when(trade.getProcessModel()).thenReturn(processModel);
            when(trade.getTradeProtocolModel()).thenReturn(processModel);
            when(trade.getId()).thenReturn(TRADE_ID);
            when(trade.disputeStateProperty()).thenReturn(disputeState);
            when(trade.getDisputeState()).thenAnswer(invocation -> disputeState.get());
            protocol = new TestDisputeProtocol(trade);
        }
    }

    // Records which messages the protocol handles and with which tasks, without running the tasks.
    private static final class TestDisputeProtocol extends DisputeProtocol {
        private final List<TradeMessage> handledMessages = new ArrayList<>();
        private Class<? extends Task<TradeModel>>[] selectedTasks;

        private TestDisputeProtocol(Trade trade) {
            super(trade);
        }

        @Override
        protected FluentProtocol expect(FluentProtocol.Condition condition) {
            handledMessages.add(condition.getMessage());
            return new FluentProtocol(this) {
                @Override
                public FluentProtocol setup(Setup setup) {
                    selectedTasks = setup.getTasks();
                    return this;
                }

                @Override
                public FluentProtocol executeTasks() {
                    return this;
                }
            };
        }
    }
}
