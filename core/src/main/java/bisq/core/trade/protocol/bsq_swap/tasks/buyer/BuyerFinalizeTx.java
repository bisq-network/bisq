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

package bisq.core.trade.protocol.bsq_swap.tasks.buyer;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class BuyerFinalizeTx extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public BuyerFinalizeTx(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            TradeWalletService tradeWalletService = protocolModel.getTradeWalletService();
            BtcWalletService btcWalletService = protocolModel.getBtcWalletService();
            NetworkParameters params = btcWalletService.getParams();

            Transaction sellersTransaction = btcWalletService.getTxFromSerializedTx(tradePeer.getTx());

            // The inputs from the deserialized tx do not have connected outpoints and no value assigned.
            // We reapply that by using the RawTransactionInputs
            int buyersInputSize = Objects.requireNonNull(protocolModel.getInputs()).size();
            List<TransactionInput> sellersInputs = sellersTransaction.getInputs().stream()
                    .filter(i -> i.getIndex() >= buyersInputSize)
                    .collect(Collectors.toList());

            List<RawTransactionInput> rawInputs = Objects.requireNonNull(tradePeer.getInputs());
            List<TransactionInput> sellersBtcInputs = new ArrayList<>();
            for (int i = 0; i < sellersInputs.size(); i++) {
                TransactionInput transactionInput = sellersInputs.get(i);
                RawTransactionInput rawTransactionInput = rawInputs.get(i);
                Coin value = Coin.valueOf(rawTransactionInput.value);
                Transaction parentTx = new Transaction(params, rawTransactionInput.parentTransaction);
                TransactionOutPoint transactionOutPoint = new TransactionOutPoint(params, rawTransactionInput.index, parentTx);
                TransactionInput result = new TransactionInput(params,
                        parentTx,
                        transactionInput.getScriptBytes(),
                        transactionOutPoint,
                        value);
                if (transactionInput.hasWitness()) {
                    result.setWitness(transactionInput.getWitness());
                }
                sellersBtcInputs.add(result);
            }

            TransactionOutput sellersBsqPayout = sellersTransaction.getOutput(0);
            tradePeer.setBsqAddress(sellersBsqPayout.getScriptPubKey().getToAddress(params).toString());
            tradePeer.setPayout(sellersBsqPayout.getValue().getValue());

            // As sellersBtcChangeOutput is optional we need to check if it is present.
            // If it is not present the last output is my Btc payout output.
            TransactionOutput lastPayout = sellersTransaction.getOutput(sellersTransaction.getOutputs().size() - 1);
            Address addressOfLastOutput = lastPayout.getScriptPubKey().getToAddress(params);
            String myBtcPayoutOutput = Objects.requireNonNull(protocolModel.getBtcAddress());
            if (!myBtcPayoutOutput.equals(addressOfLastOutput.toString())) {
                // last output is peers change output
                tradePeer.setBtcAddress(addressOfLastOutput.toString());
                tradePeer.setChange(lastPayout.getValue().getValue());
            }

            List<RawTransactionInput> buyersBsqInputs = Objects.requireNonNull(protocolModel.getInputs());

            Coin sellersBsqPayoutAmount = Coin.valueOf(tradePeer.getPayout());
            String sellersBsqPayoutAddress = tradePeer.getBsqAddress();

            Coin buyersBsqChangeAmount = Coin.valueOf(protocolModel.getChange());
            String buyersBsqChangeAddress = protocolModel.getBsqAddress();

            Coin buyersBtcPayoutAmount = Coin.valueOf(protocolModel.getPayout());
            String buyersBtcPayoutAddress = protocolModel.getBtcAddress();

            Coin sellersBtcChangeAmount = Coin.valueOf(tradePeer.getChange());
            String sellersBtcChangeAddress = tradePeer.getBtcAddress();

            Transaction transaction = tradeWalletService.buyerBuildBsqSwapTx(
                    buyersBsqInputs,
                    sellersBtcInputs,
                    sellersBsqPayoutAmount,
                    sellersBsqPayoutAddress,
                    buyersBsqChangeAmount,
                    buyersBsqChangeAddress,
                    buyersBtcPayoutAmount,
                    buyersBtcPayoutAddress,
                    sellersBtcChangeAmount,
                    sellersBtcChangeAddress
            );

            log.error("unsigned tx {}", transaction.toString());
            byte[] serialized = transaction.bitcoinSerialize();
            checkArgument(Arrays.equals(serialized, tradePeer.getTx()), "Peers serialized tx must match our own created unsigned tx");

            // Sign tx
            int myInputSize = buyersBsqInputs.size();
            List<TransactionInput> myInputs = transaction.getInputs().stream()
                    .filter(input -> input.getIndex() < myInputSize)
                    .collect(Collectors.toList());
            protocolModel.getBsqWalletService().signBsqSwapTransaction(transaction, myInputs);

            log.error("Buyers signed transaction {}", transaction);
            protocolModel.applyTransaction(transaction);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
