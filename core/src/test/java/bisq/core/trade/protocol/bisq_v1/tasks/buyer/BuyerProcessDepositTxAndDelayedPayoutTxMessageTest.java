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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.TradeManager;
import bisq.core.trade.model.bisq_v1.BuyerAsMakerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.protocol.bisq_v1.model.ProcessModel;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.SegwitAddress;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.script.ScriptBuilder;
import org.bitcoinj.wallet.Wallet;

import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BuyerProcessDepositTxAndDelayedPayoutTxMessageTest {
    private static final NetworkParameters PARAMS = MainNetParams.get();
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @Test
    void runAcceptsDelayedPayoutTxWithNonZeroLockTime() {
        Transaction depositTx = depositTx(0);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);
        Wallet wallet = mock(Wallet.class);
        Trade trade = trade(depositTx, delayedPayoutTx.bitcoinSerialize(), wallet);

        try (MockedStatic<WalletService> walletService = mockStatic(WalletService.class)) {
            walletService.when(() -> WalletService.maybeAddSelfTxToWallet(any(Transaction.class), same(wallet)))
                    .thenReturn(depositTx);

            new BuyerProcessDepositTxAndDelayedPayoutTxMessage(taskRunner(), trade).run();

            walletService.verify(() -> WalletService.maybeAddSelfTxToWallet(any(Transaction.class), same(wallet)));
            verify(trade).applyDepositTx(depositTx);
            verify(trade).applyDelayedPayoutTxBytes(delayedPayoutTx.bitcoinSerialize());
        }
    }

    @Test
    void runUsesPreparedDepositTxForBuyerAsMaker() {
        Transaction preparedDepositTx = depositTx(0);
        Transaction delayedPayoutTx = delayedPayoutTx(preparedDepositTx, 144);
        Wallet wallet = mock(Wallet.class);
        BuyerAsMakerTrade trade = buyerAsMakerTrade(preparedDepositTx, delayedPayoutTx.bitcoinSerialize(), wallet);

        try (MockedStatic<WalletService> walletService = mockStatic(WalletService.class)) {
            walletService.when(() -> WalletService.maybeAddSelfTxToWallet(any(Transaction.class), same(wallet)))
                    .thenReturn(preparedDepositTx);

            new BuyerProcessDepositTxAndDelayedPayoutTxMessage(taskRunner(), trade).run();

            walletService.verify(() -> WalletService.maybeAddSelfTxToWallet(any(Transaction.class), same(wallet)));
            verify(trade).applyDepositTx(preparedDepositTx);
            verify(trade).applyDelayedPayoutTxBytes(delayedPayoutTx.bitcoinSerialize());
        }
    }

    @Test
    void runRejectsMismatchedDelayedPayoutTxBeforeWalletMutation() {
        Transaction depositTx = depositTx(0);
        Transaction delayedPayoutTx = delayedPayoutTx(depositTx, 144);
        Transaction otherDelayedPayoutTx = delayedPayoutTx(depositTx, 145);
        Wallet wallet = mock(Wallet.class);
        Trade trade = trade(depositTx,
                otherDelayedPayoutTx.bitcoinSerialize(),
                delayedPayoutTx.bitcoinSerialize(),
                wallet);

        try (MockedStatic<WalletService> walletService = mockStatic(WalletService.class)) {
            new BuyerProcessDepositTxAndDelayedPayoutTxMessage(taskRunner(), trade).run();

            walletService.verifyNoInteractions();
            verify(trade, never()).applyDepositTx(any(Transaction.class));
            verify(trade, never()).applyDelayedPayoutTxBytes(any(byte[].class));
        }
    }

    // Direct simulation of the crit.md attack: the seller sends the correct negotiated delayed
    // payout tx but swaps in a different deposit tx that is not the buyer's locally known deposit
    // tx and is not the tx the delayed payout tx spends. The buyer must reject the message before
    // any wallet or trade-state mutation.
    @Test
    void runRejectsPeerDepositTxNotBoundToBuyersDepositTxBeforeWalletMutation() {
        Transaction myDepositTx = depositTx(0);
        Transaction delayedPayoutTx = delayedPayoutTx(myDepositTx, 144);
        Transaction attackerDepositTx = depositTx(1);
        Wallet wallet = mock(Wallet.class);
        Trade trade = trade(myDepositTx,
                attackerDepositTx,
                delayedPayoutTx.bitcoinSerialize(),
                delayedPayoutTx.bitcoinSerialize(),
                wallet);

        try (MockedStatic<WalletService> walletService = mockStatic(WalletService.class)) {
            new BuyerProcessDepositTxAndDelayedPayoutTxMessage(taskRunner(), trade).run();

            walletService.verifyNoInteractions();
            verify(trade, never()).applyDepositTx(any(Transaction.class));
            verify(trade, never()).applyDelayedPayoutTxBytes(any(byte[].class));
        }
    }

    // Exercises the checkDelayedPayoutTxInput binding through the full task path: peer sends its
    // own deposit tx bytes together with a delayed payout tx whose input does not spend output 0
    // of that deposit tx. Must reject before any wallet or trade-state mutation.
    @Test
    void runRejectsPeerDelayedPayoutTxNotSpendingDepositTxOutputZeroBeforeWalletMutation() {
        Transaction myDepositTx = depositTx(0);
        Transaction otherDepositTx = depositTx(1);
        Transaction delayedPayoutTx = delayedPayoutTx(otherDepositTx, 144);
        Wallet wallet = mock(Wallet.class);
        Trade trade = trade(myDepositTx,
                myDepositTx,
                delayedPayoutTx.bitcoinSerialize(),
                delayedPayoutTx.bitcoinSerialize(),
                wallet);

        try (MockedStatic<WalletService> walletService = mockStatic(WalletService.class)) {
            new BuyerProcessDepositTxAndDelayedPayoutTxMessage(taskRunner(), trade).run();

            walletService.verifyNoInteractions();
            verify(trade, never()).applyDepositTx(any(Transaction.class));
            verify(trade, never()).applyDelayedPayoutTxBytes(any(byte[].class));
        }
    }

    private static Trade trade(Transaction depositTx, byte[] delayedPayoutTxBytes, Wallet wallet) {
        return trade(depositTx, delayedPayoutTxBytes, delayedPayoutTxBytes, wallet);
    }

    private static Trade trade(Transaction myDepositTx,
                               Transaction messageDepositTx,
                               byte[] delayedPayoutTxBytes,
                               byte[] messageDelayedPayoutTxBytes,
                               Wallet wallet) {
        Trade trade = mock(Trade.class);
        ProcessModel processModel = processModel(myDepositTx,
                messageDepositTx,
                null,
                messageDelayedPayoutTxBytes,
                wallet);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getDelayedPayoutTxBytes()).thenReturn(delayedPayoutTxBytes);
        when(trade.getTradeState()).thenReturn(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);
        when(trade.getId()).thenReturn("trade-id");
        return trade;
    }

    private static BuyerAsMakerTrade buyerAsMakerTrade(Transaction preparedDepositTx,
                                                       byte[] delayedPayoutTxBytes,
                                                       Wallet wallet) {
        BuyerAsMakerTrade trade = mock(BuyerAsMakerTrade.class);
        ProcessModel processModel = processModel(preparedDepositTx,
                preparedDepositTx.bitcoinSerialize(),
                delayedPayoutTxBytes,
                wallet);
        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getDelayedPayoutTxBytes()).thenReturn(delayedPayoutTxBytes);
        when(trade.getTradeState()).thenReturn(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);
        when(trade.getId()).thenReturn("trade-id");
        return trade;
    }

    @SuppressWarnings("unchecked")
    private static TaskRunner<Trade> taskRunner() {
        return mock(TaskRunner.class);
    }

    private static Trade trade(Transaction depositTx,
                               byte[] delayedPayoutTxBytes,
                               byte[] messageDelayedPayoutTxBytes,
                               Wallet wallet) {
        Trade trade = mock(Trade.class);
        ProcessModel processModel = processModel(depositTx,
                depositTx,
                null,
                messageDelayedPayoutTxBytes,
                wallet);

        when(trade.getProcessModel()).thenReturn(processModel);
        when(trade.getDelayedPayoutTxBytes()).thenReturn(delayedPayoutTxBytes);
        when(trade.getTradeState()).thenReturn(Trade.State.BUYER_RECEIVED_DEPOSIT_TX_PUBLISHED_MSG);
        when(trade.getId()).thenReturn("trade-id");
        return trade;
    }

    private static ProcessModel processModel(Transaction depositTx,
                                             byte[] preparedDepositTx,
                                             byte[] messageDelayedPayoutTxBytes,
                                             Wallet wallet) {
        return processModel(depositTx, depositTx, preparedDepositTx, messageDelayedPayoutTxBytes, wallet);
    }

    private static ProcessModel processModel(Transaction myDepositTx,
                                             Transaction messageDepositTx,
                                             byte[] preparedDepositTx,
                                             byte[] messageDelayedPayoutTxBytes,
                                             Wallet wallet) {
        ProcessModel processModel = mock(ProcessModel.class);
        BtcWalletService btcWalletService = mock(BtcWalletService.class);
        TradeManager tradeManager = mock(TradeManager.class);

        when(processModel.getTradeMessage()).thenReturn(message(messageDepositTx, messageDelayedPayoutTxBytes));
        when(processModel.getBtcWalletService()).thenReturn(btcWalletService);
        when(processModel.getTradePeer()).thenReturn(mock(TradingPeer.class));
        when(processModel.getDepositTx()).thenReturn(myDepositTx);
        when(processModel.getPreparedDepositTx()).thenReturn(preparedDepositTx);
        when(processModel.getTradeManager()).thenReturn(tradeManager);
        when(processModel.getTempTradingPeerNodeAddress()).thenReturn(NODE_ADDRESS);
        when(btcWalletService.getParams()).thenReturn(PARAMS);
        when(btcWalletService.getWallet()).thenReturn(wallet);
        return processModel;
    }

    private static DepositTxAndDelayedPayoutTxMessage message(Transaction depositTx,
                                                              byte[] delayedPayoutTxBytes) {
        return new DepositTxAndDelayedPayoutTxMessage("uid",
                "trade-id",
                NODE_ADDRESS,
                depositTx.bitcoinSerialize(),
                delayedPayoutTxBytes,
                null);
    }

    private static Transaction depositTx(long outpointIndex) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(new TransactionInput(PARAMS,
                transaction,
                new byte[]{},
                new TransactionOutPoint(PARAMS, outpointIndex, Sha256Hash.ZERO_HASH),
                Coin.valueOf(2_000)));
        transaction.addOutput(Coin.valueOf(1_000), ScriptBuilder.createP2WPKHOutputScript(new ECKey()));
        return transaction;
    }

    private static Transaction delayedPayoutTx(Transaction depositTx, long lockTime) {
        Transaction transaction = new Transaction(PARAMS);
        transaction.addInput(depositTx.getOutput(0));
        transaction.getInput(0).setSequenceNumber(TransactionInput.NO_SEQUENCE - 1);
        transaction.setLockTime(lockTime);
        transaction.addOutput(Coin.valueOf(500),
                Address.fromString(PARAMS, SegwitAddress.fromKey(PARAMS, new ECKey()).toString()));
        return transaction;
    }
}
