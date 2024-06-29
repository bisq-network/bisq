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

package bisq.core.trade.protocol.bisq_v5.tasks.seller;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;
import bisq.core.trade.protocol.bisq_v5.model.StagedPayoutTxParameters;

import bisq.common.taskrunner.TaskRunner;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SellerCreatesWarningTx extends TradeTask {
    public SellerCreatesWarningTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            String tradeId = processModel.getOffer().getId();

            Transaction depositTx = processModel.getDepositTx();
            TransactionOutput depositTxOutput = depositTx.getOutput(0);
            long lockTime = trade.getLockTime();
            byte[] buyerPubKey = processModel.getTradePeer().getMultiSigPubKey();
            byte[] sellerPubKey = processModel.getMyMultiSigPubKey();
            long claimDelay = StagedPayoutTxParameters.CLAIM_DELAY;
            long miningFee = StagedPayoutTxParameters.getWarningTxMiningFee(trade.getDepositTxFeeRate());
            AddressEntry feeBumpAddressEntry = btcWalletService.getOrCreateAddressEntry(tradeId, AddressEntry.Context.WARNING_TX_FEE_BUMP);
            Tuple2<Long, String> feeBumpOutputAmountAndAddress = new Tuple2<>(StagedPayoutTxParameters.WARNING_TX_FEE_BUMP_OUTPUT_VALUE, feeBumpAddressEntry.getAddressString());
            Transaction unsignedWarningTx = tradeWalletService.createUnsignedWarningTx(false,
                    depositTxOutput,
                    lockTime,
                    buyerPubKey,
                    sellerPubKey,
                    claimDelay,
                    miningFee,
                    feeBumpOutputAmountAndAddress);
            processModel.setWarningTx(unsignedWarningTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
