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

package bisq.core.trade.protocol.bisq_v1.tasks.mediation;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.WalletService;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.UserThread;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.wallet.Wallet;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.PayoutTxValidation.checkPayoutTx;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class ProcessMediatedPayoutTxPublishedMessage extends TradeTask {
    public ProcessMediatedPayoutTxPublishedMessage(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            MediatedPayoutTxPublishedMessage message = (MediatedPayoutTxPublishedMessage) processModel.getTradeMessage();
            checkNotNull(message);

            // update to the latest peer address of our peer if the message is correct
            trade.setTradingPeerNodeAddress(processModel.getTempTradingPeerNodeAddress());

            if (trade.getPayoutTx() == null) {
                BtcWalletService btcWalletService = processModel.getBtcWalletService();
                Wallet wallet = btcWalletService.getWallet();
                TradingPeer tradingPeer = processModel.getTradePeer();

                Contract contract = checkNotNull(trade.getContract(), "contract must not be null");
                boolean isMyRoleBuyer = contract.isMyRoleBuyer(processModel.getPubKeyRing());
                String myPayoutAddressString = btcWalletService.getOrCreateAddressEntry(trade.getId(),
                        AddressEntry.Context.TRADE_PAYOUT).getAddressString();
                String peersPayoutAddressString = tradingPeer.getPayoutAddressString();
                String buyerPayoutAddressString = isMyRoleBuyer ? myPayoutAddressString : peersPayoutAddressString;
                String sellerPayoutAddressString = isMyRoleBuyer ? peersPayoutAddressString : myPayoutAddressString;
                byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
                byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
                byte[] buyerMultiSigPubKey = isMyRoleBuyer ? myMultiSigPubKey : peersMultiSigPubKey;
                byte[] sellerMultiSigPubKey = isMyRoleBuyer ? peersMultiSigPubKey : myMultiSigPubKey;
                Transaction depositTx = checkNotNull(trade.getDepositTx(), "trade.getDepositTx() must not be null");
                Coin buyerPayoutAmount = Coin.valueOf(processModel.getBuyerPayoutAmountFromMediation());
                Coin sellerPayoutAmount = Coin.valueOf(processModel.getSellerPayoutAmountFromMediation());
                byte[] serializedPayoutTx = message.getPayoutTx();

                byte[] payoutTx = checkPayoutTx(serializedPayoutTx,
                        btcWalletService,
                        depositTx,
                        buyerPayoutAmount,
                        sellerPayoutAmount,
                        buyerPayoutAddressString,
                        sellerPayoutAddressString,
                        buyerMultiSigPubKey,
                        sellerMultiSigPubKey);

                Transaction committedMediatedPayoutTx = WalletService.maybeAddNetworkTxToWallet(payoutTx, wallet);
                trade.setPayoutTx(committedMediatedPayoutTx);
                log.info("MediatedPayoutTx received from peer.  Txid: {}\nhex: {}",
                        committedMediatedPayoutTx.getTxId().toString(), Utils.HEX.encode(committedMediatedPayoutTx.bitcoinSerialize()));

                trade.setMediationResultState(MediationResultState.RECEIVED_PAYOUT_TX_PUBLISHED_MSG);

                btcWalletService.resetCoinLockedInMultiSigAddressEntry(trade.getId());

                // We need to delay that call as we might get executed at startup after mailbox messages are
                // applied where we iterate over our pending trades. The closeDisputedTrade method would remove
                // that trade from the list causing a ConcurrentModificationException.
                // To avoid that we delay for one render frame.
                UserThread.execute(() -> processModel.getTradeManager()
                        .closeDisputedTrade(trade.getId(), Trade.DisputeState.MEDIATION_CLOSED));
            } else {
                log.info("We got the payout tx already set from BuyerSetupPayoutTxListener and do nothing here. trade ID={}", trade.getId());
            }

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
