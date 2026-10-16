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

package bisq.core.trade.protocol.bisq_v1.tasks.seller;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.TradeModel;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.params.MainNetParams;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// The seller can confirm the payment receipt again after the trade completed, and step 4 has released the
// seller's payout address entry by then.
public class SellerSignAndFinalizePayoutTxTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final String TRADE_ID = "trade-id";

    private Trade trade;
    private BtcWalletService btcWalletService;
    private final AtomicBoolean completed = new AtomicBoolean();
    private final AtomicReference<String> errorMessage = new AtomicReference<>();

    @BeforeEach
    public void setUp() {
        trade = mock(Trade.class);
        ProcessModel processModel = mock(ProcessModel.class);
        btcWalletService = mock(BtcWalletService.class);
        Offer offer = mock(Offer.class);
        Contract contract = mock(Contract.class);
        TradingPeer tradingPeer = new TradingPeer();
        String buyerPayoutAddress = SegwitAddress.fromKey(PARAMS, new ECKey()).toString();

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getId()).thenReturn(TRADE_ID);
        when(trade.getAmount()).thenReturn(Coin.valueOf(1_000_000));
        when(trade.getOffer()).thenReturn(offer);
        when(trade.getContract()).thenReturn(contract);
        when(offer.getId()).thenReturn(TRADE_ID);
        when(offer.getBuyerSecurityDeposit()).thenReturn(Coin.valueOf(150_000));
        when(offer.getSellerSecurityDeposit()).thenReturn(Coin.valueOf(150_000));
        when(contract.getBuyerPayoutAddressString()).thenReturn(buyerPayoutAddress);
        when(contract.getSellerPayoutAddressString()).thenReturn(SegwitAddress.fromKey(PARAMS, new ECKey()).toString());
        tradingPeer.setPayoutAddressString(buyerPayoutAddress);
        when(processModel.getOffer()).thenReturn(offer);
        when(processModel.getTradePeer()).thenReturn(tradingPeer);
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradeManager()).thenReturn(mock(TradeManager.class));
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        when(btcWalletService.getAddressEntry(TRADE_ID, AddressEntry.Context.TRADE_PAYOUT))
                .thenReturn(Optional.empty());
    }

    @SuppressWarnings("unchecked")
    private void runTask() {
        // same unchecked narrowing as TradeTaskRunner: tasks declare (TaskRunner, Trade)
        // constructors, so the runner must look them up with Trade.class
        TaskRunner<TradeModel> taskRunner = new TaskRunner<>(trade,
                (Class<TradeModel>) (Class<?>) Trade.class,
                () -> completed.set(true),
                errorMessage::set);
        taskRunner.addTasks(SellerSignAndFinalizePayoutTx.class);
        taskRunner.run();
    }

    @Test
    public void repeatedConfirmationKeepsThePayoutTx() {
        when(trade.getPayoutTx()).thenReturn(new Transaction(PARAMS));

        runTask();

        assertNull(errorMessage.get());
        assertTrue(completed.get());
        verify(trade, never()).setPayoutTx(any());
        verify(btcWalletService, never()).getAddressEntry(any(), any());
    }

    @Test
    public void firstConfirmationRequiresThePayoutAddressEntry() {
        runTask();

        assertFalse(completed.get());
        assertTrue(errorMessage.get().contains("Seller payout address entry must exist"));
    }
}
