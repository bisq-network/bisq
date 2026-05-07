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
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.SetupPayoutTxListener;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;

import lombok.extern.slf4j.Slf4j;

import static bisq.core.trade.validation.PayoutTxValidation.checkPayoutTx;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SetupMediatedPayoutTxListener extends SetupPayoutTxListener {
    public SetupMediatedPayoutTxListener(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            super.run();

        } catch (Throwable t) {
            failed(t);
        }
    }

    @Override
    protected void setState() {
        trade.setMediationResultState(MediationResultState.PAYOUT_TX_SEEN_IN_NETWORK);
        if (trade.getPayoutTx() != null) {
            processModel.getTradeManager().closeDisputedTrade(trade.getId(), Trade.DisputeState.MEDIATION_CLOSED);
        }
        processModel.getTradeManager().requestPersistence();
    }

    @Override
    protected void validatePayoutTx(Transaction payoutTx) {
        BtcWalletService btcWalletService = processModel.getBtcWalletService();
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
        NetworkParameters params = btcWalletService.getParams();

        checkPayoutTx(payoutTx,
                depositTx,
                buyerPayoutAmount,
                sellerPayoutAmount,
                buyerPayoutAddressString,
                sellerPayoutAddressString,
                buyerMultiSigPubKey,
                sellerMultiSigPubKey,
                params);
    }
}
