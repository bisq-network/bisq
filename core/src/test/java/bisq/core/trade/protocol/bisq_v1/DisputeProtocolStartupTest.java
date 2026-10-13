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

import bisq.core.btc.listeners.AddressConfidenceListener;
import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.SellerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.FluentProtocol;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.tasks.mediation.SetupMediatedPayoutTxListener;

import bisq.network.p2p.P2PService;
import bisq.network.p2p.mailbox.MailboxMessageService;

import bisq.common.taskrunner.Task;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.params.MainNetParams;

import javafx.beans.property.SimpleObjectProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.mockito.ArgumentCaptor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A trader who has accepted the mediation result (own signature of the mediated payout) watches the
 * wallet for the peer's payout again at startup, while the payout is not known.
 */
class DisputeProtocolStartupTest {
    private static final byte[] OWN_SIGNATURE = new byte[]{1};
    private static final Address PAYOUT_ADDRESS = SegwitAddress.fromKey(MainNetParams.get(), new ECKey());

    @ParameterizedTest
    @EnumSource(value = Trade.Phase.class, names = {"DEPOSIT_CONFIRMED", "FIAT_SENT", "FIAT_RECEIVED"})
    void mediatedPayoutListenerIsSetUpAfterOwnAcceptance(Trade.Phase phase) {
        TestDisputeProtocol protocol = protocol(phase, OWN_SIGNATURE);

        protocol.onInitialized();

        assertEquals(List.of(SetupMediatedPayoutTxListener.class), protocol.executedTasks);
    }

    @Test
    void noMediatedPayoutListenerBeforeOwnAcceptance() {
        TestDisputeProtocol protocol = protocol(Trade.Phase.FIAT_SENT, null);

        protocol.onInitialized();

        assertTrue(protocol.executedTasks.isEmpty());
    }

    @ParameterizedTest
    @EnumSource(value = Trade.Phase.class,
            names = {"TAKER_FEE_PUBLISHED", "DEPOSIT_PUBLISHED", "PAYOUT_PUBLISHED", "WITHDRAWN"})
    void noMediatedPayoutListenerInOtherPhases(Trade.Phase phase) {
        TestDisputeProtocol protocol = protocol(phase, OWN_SIGNATURE);

        protocol.onInitialized();

        assertTrue(protocol.executedTasks.isEmpty());
    }

    // Runs the real startup path and the real SetupMediatedPayoutTxListener: the listener for the payout address is
    // registered in the wallet, not only chosen as the task
    @Test
    void mediatedPayoutListenerIsRegisteredInTheWalletAfterOwnAcceptance() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        Trade trade = tradeWithWallet(OWN_SIGNATURE, btcWalletService);

        new DisputeProtocol(trade).onInitialized();

        ArgumentCaptor<AddressConfidenceListener> listener = ArgumentCaptor.forClass(AddressConfidenceListener.class);
        verify(btcWalletService).addAddressConfidenceListener(listener.capture());
        assertEquals(PAYOUT_ADDRESS, listener.getValue().getAddress());
        // A failed task would set an error message on the trade
        verify(trade, never()).setErrorMessage(anyString());
    }

    @Test
    void noWalletListenerBeforeOwnAcceptance() {
        BtcWalletService btcWalletService = mock(BtcWalletService.class);

        new DisputeProtocol(tradeWithWallet(null, btcWalletService)).onInitialized();

        verify(btcWalletService, never()).addAddressConfidenceListener(any(AddressConfidenceListener.class));
    }

    private static TestDisputeProtocol protocol(Trade.Phase phase, byte[] ownSignature) {
        return new TestDisputeProtocol(trade(phase, ownSignature));
    }

    // A trade in FIAT_SENT with the payout address entry in the wallet and no payout yet
    private static Trade tradeWithWallet(byte[] ownSignature, BtcWalletService btcWalletService) {
        Trade trade = trade(Trade.Phase.FIAT_SENT, ownSignature);
        ProcessModel processModel = trade.getProcessModel();
        Offer offer = mock(Offer.class);
        when(offer.getId()).thenReturn("trade-id");
        when(processModel.getOffer()).thenReturn(offer);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        AddressEntry payoutAddressEntry = mock(AddressEntry.class);
        when(payoutAddressEntry.getAddress()).thenReturn(PAYOUT_ADDRESS);
        when(btcWalletService.getAddressEntry("trade-id", AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.of(payoutAddressEntry));
        Transaction depositTx = mock(Transaction.class);
        when(depositTx.getConfidence()).thenReturn(mock(TransactionConfidence.class));
        when(trade.getDepositTx()).thenReturn(depositTx);
        when(trade.stateProperty())
                .thenReturn(new SimpleObjectProperty<>(Trade.State.SELLER_RECEIVED_FIAT_PAYMENT_INITIATED_MSG));
        return trade;
    }

    private static Trade trade(Trade.Phase phase, byte[] ownSignature) {
        P2PService p2PService = mock(P2PService.class);
        when(p2PService.getMailboxMessageService()).thenReturn(mock(MailboxMessageService.class));
        ProcessModel processModel = mock(ProcessModel.class);
        when(processModel.getP2PService()).thenReturn(p2PService);
        when(processModel.getMediatedPayoutTxSignature()).thenReturn(ownSignature);

        // TradeTaskRunner finds the task constructor two classes above the trade class, as for a SellerAsMakerTrade
        Trade trade = mock(SellerTrade.class);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getTradeProtocolModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn("trade-id");
        when(trade.getTradePhase()).thenReturn(phase);
        return trade;
    }

    // Records the tasks the protocol runs at startup, without running them.
    private static final class TestDisputeProtocol extends DisputeProtocol {
        private final List<Class<? extends Task<TradeModel>>> executedTasks = new ArrayList<>();

        private TestDisputeProtocol(Trade trade) {
            super(trade);
        }

        @Override
        protected FluentProtocol given(FluentProtocol.Condition condition) {
            return new FluentProtocol(this) {
                private Class<? extends Task<TradeModel>>[] tasks;

                {
                    condition(condition);
                }

                @Override
                public FluentProtocol setup(Setup setup) {
                    tasks = setup.getTasks();
                    return this;
                }

                // Records the tasks only if the condition is met, as executeTasks would run them
                @Override
                public FluentProtocol executeTasks() {
                    return run(() -> executedTasks.addAll(List.of(tasks)));
                }
            };
        }
    }
}
