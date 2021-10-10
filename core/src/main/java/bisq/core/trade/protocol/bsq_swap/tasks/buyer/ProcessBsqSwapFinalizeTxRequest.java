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
import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsq_swap.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;
import bisq.core.trade.protocol.messages.bsq_swap.BsqSwapFinalizeTxRequest;
import bisq.core.util.Validator;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class ProcessBsqSwapFinalizeTxRequest extends BsqSwapTask {
    @SuppressWarnings({"unused"})
    public ProcessBsqSwapFinalizeTxRequest(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            BsqSwapFinalizeTxRequest request = checkNotNull((BsqSwapFinalizeTxRequest) protocolModel.getTradeMessage());
            checkNotNull(request);
            Validator.checkTradeId(protocolModel.getOfferId(), request);

            // We will use only the sellers inputs from the tx so we do not verify anything else
            byte[] tx = request.getTx();
            Transaction sellersTransaction = protocolModel.getBtcWalletService().getTxFromSerializedTx(tx);
            List<RawTransactionInput> rawInputs = request.getBtcInputs();
            checkArgument(!rawInputs.isEmpty(), "Sellers BTC inputs must not be empty");

            int buyersInputSize = Objects.requireNonNull(protocolModel.getInputs()).size();
            List<TransactionInput> sellersBtcInputs = sellersTransaction.getInputs().stream()
                    .filter(i -> i.getIndex() >= buyersInputSize)
                    .collect(Collectors.toList());
            checkArgument(sellersBtcInputs.size() == rawInputs.size(),
                    "Number of inputs in tx must match the number of rawInputs");

            boolean hasUnSignedInputs = sellersBtcInputs.stream().anyMatch(i -> i.getScriptSig() == null && !i.hasWitness());
            checkArgument(!hasUnSignedInputs, "Inputs from tx has unsigned inputs");

            long sumInputs = rawInputs.stream().mapToLong(rawTransactionInput -> rawTransactionInput.value).sum();
            int sellersTxSize = BsqSwapCalculation.getVBytesSize(rawInputs, request.getBtcChange());
            long sellersBtcInputAmount = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, getSellersTradeFee()).getValue();
            // It can be that there have been dust change which got added to miner fees
            checkArgument(sumInputs >= sellersBtcInputAmount,
                    "Sellers BTC input amount do not match our calculated required BTC input amount");

            long change = request.getBtcChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BTC change must be 0 or above dust");

            int buyersTxSize = BsqSwapCalculation.getVBytesSize(protocolModel.getInputs(), protocolModel.getChange());
            long buyersTxFee = BsqSwapCalculation.getAdjustedTxFee(trade, buyersTxSize, getBuyersTradeFee());
            long sellersTxFee = BsqSwapCalculation.getAdjustedTxFee(trade, sellersTxSize, getSellersTradeFee());
            long buyersBtcPayout = protocolModel.getPayout();
            long expectedChange = sumInputs - buyersBtcPayout - sellersTxFee - buyersTxFee;
            boolean isChangeAboveDust = Restrictions.isAboveDust(Coin.valueOf(expectedChange));
            if (expectedChange != change && isChangeAboveDust) {
                log.warn("Sellers BTC change is not as expected. This can happen if fee estimation for inputs did not " +
                        "succeed (e.g. dust change, max. iterations reached,...");
                log.warn("buyersBtcPayout={}, sumInputs={}, sellersTxFee={}, buyersTxFee={}, expectedChange={}, change={}",
                        buyersBtcPayout, sumInputs, sellersTxFee, buyersTxFee, expectedChange, change);
            }
            checkArgument(change <= expectedChange,
                    "Change must be smaller or equal to expectedChange");

            NetworkParameters params = protocolModel.getBtcWalletService().getParams();
            String sellersBsqPayoutAddress = request.getBsqPayoutAddress();
            checkNotNull(sellersBsqPayoutAddress, "sellersBsqPayoutAddress must not be null");
            checkArgument(!sellersBsqPayoutAddress.isEmpty(), "sellersBsqPayoutAddress must not be empty");
            Address.fromString(params, sellersBsqPayoutAddress); // If address is not a BTC address it throws an exception

            String sellersBtcChangeAddress = request.getBtcChangeAddress();
            checkNotNull(sellersBtcChangeAddress, "sellersBtcChangeAddress must not be null");
            checkArgument(!sellersBtcChangeAddress.isEmpty(), "sellersBtcChangeAddress must not be empty");
            Address.fromString(params, sellersBtcChangeAddress); // If address is not a BTC address it throws an exception

            // Apply data
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            tradePeer.setTx(tx);
            tradePeer.setTransactionInputs(sellersBtcInputs);
            tradePeer.setInputs(rawInputs);
            tradePeer.setChange(change);
            tradePeer.setBtcAddress(sellersBtcChangeAddress);
            tradePeer.setBsqAddress(sellersBsqPayoutAddress);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
