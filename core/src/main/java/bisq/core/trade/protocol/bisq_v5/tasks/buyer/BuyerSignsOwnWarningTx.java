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

package bisq.core.trade.protocol.bisq_v5.tasks.buyer;

import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.crypto.DeterministicKey;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerSignsOwnWarningTx extends TradeTask {
    public BuyerSignsOwnWarningTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            TradeWalletService tradeWalletService = processModel.getTradeWalletService();
            BtcWalletService btcWalletService = processModel.getBtcWalletService();
            String tradeId = processModel.getOffer().getId();

            Transaction unsignedWarningTx = processModel.getUnsignedWarningTx();
            Transaction depositTx = btcWalletService.getTxFromSerializedTx(processModel.getPreparedDepositTx());
            TransactionOutput depositTxOutput = depositTx.getOutput(0);
            byte[] buyerPubKey = processModel.getMyMultiSigPubKey();
            DeterministicKey myMultiSigKeyPair = btcWalletService.getMultiSigKeyPair(tradeId, buyerPubKey);
            byte[] sellerPubKey = processModel.getTradePeer().getMultiSigPubKey();
            byte[] signature = tradeWalletService.signWarningTx(unsignedWarningTx,
                    depositTxOutput,
                    myMultiSigKeyPair,
                    buyerPubKey,
                    sellerPubKey);
            processModel.setWarningTxBuyerSignature(signature);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
