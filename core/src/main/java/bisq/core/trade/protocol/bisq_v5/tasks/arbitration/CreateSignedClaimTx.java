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

package bisq.core.trade.protocol.bisq_v5.tasks.arbitration;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.provider.fee.FeeService;
import bisq.core.trade.model.bisq_v1.BuyerTrade;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.model.StagedPayoutTxParameters;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CreateSignedClaimTx extends TradeTask {
    public CreateSignedClaimTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            FeeService feeService = processModel.getProvider().getFeeService();
            String tradeId = processModel.getOffer().getId();
            TradingPeer tradingPeer = processModel.getTradePeer();

            TransactionOutput myWarningTxOutput = processModel.getWarningTx().getOutput(0);
            // TODO: At present, the claim tx can be picked up by the payout tx listener and set as the trade payout tx.
            //  It's not clear we want this, or perhaps we should always consider a claim as the trade payout.
            AddressEntry addressEntry = processModel.getBtcWalletService().getOrCreateAddressEntry(tradeId, AddressEntry.Context.TRADE_PAYOUT);
            Address payoutAddress = addressEntry.getAddress();
            long miningFee = StagedPayoutTxParameters.getClaimTxMiningFee(feeService.getTxFeePerVbyte().value);
            long claimDelay = StagedPayoutTxParameters.getClaimDelay();
            byte[] myMultiSigPubKey = processModel.getMyMultiSigPubKey();
            byte[] peersMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            DeterministicKey myMultiSigKeyPair = btcWalletService.getMultiSigKeyPair(tradeId, myMultiSigPubKey);
            boolean amBuyer = trade instanceof BuyerTrade;
            Transaction claimTx = tradeWalletService.createSignedClaimTx(myWarningTxOutput,
                    amBuyer,
                    claimDelay,
                    payoutAddress,
                    miningFee,
                    peersMultiSigPubKey,
                    myMultiSigKeyPair);
            processModel.setSignedClaimTx(claimTx.bitcoinSerialize());

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
