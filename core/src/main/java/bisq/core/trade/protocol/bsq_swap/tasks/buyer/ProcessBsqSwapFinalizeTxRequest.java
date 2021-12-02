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
import bisq.core.btc.wallet.WalletService;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;
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

/**
 * We cannot verify the sellers inputs if they really exist as we do not have the blockchain data for it.
 * Worst case would be that the seller pays less for miner fee as expected and thus risks to get the tx never confirmed.
 * The change output cannot be verified exactly due potential dust values and non-deterministic behaviour of the
 * fee estimation.
 * The important values for out BTC output and out BSQ change output are set already in BuyerCreatesBsqInputsAndChange
 * and are not related to the data provided by the peer. If the peers inputs would not be sufficient the tx would
 * fail anyway.
 */
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

            // We will use only the seller's BTC inputs from the tx, so we do not verify anything else.
            byte[] tx = request.getTx();
            WalletService btcWalletService = protocolModel.getBtcWalletService();
            Transaction sellersTransaction = btcWalletService.getTxFromSerializedTx(tx);
            List<RawTransactionInput> sellersRawBtcInputs = request.getBtcInputs();
            checkArgument(!sellersRawBtcInputs.isEmpty(), "SellersRawBtcInputs must not be empty");
            sellersRawBtcInputs.forEach(input -> input.validate(btcWalletService));

            List<RawTransactionInput> buyersBsqInputs = protocolModel.getInputs();
            int buyersInputSize = Objects.requireNonNull(buyersBsqInputs).size();
            List<TransactionInput> sellersBtcInputs = sellersTransaction.getInputs().stream()
                    .filter(input -> input.getIndex() >= buyersInputSize)
                    .collect(Collectors.toList());
            checkArgument(sellersBtcInputs.size() == sellersRawBtcInputs.size(),
                    "Number of sellersBtcInputs in tx must match the number of sellersRawBtcInputs");
            for (int i = 0; i < sellersBtcInputs.size(); i++) {
                String parentTxId = sellersBtcInputs.get(i).getOutpoint().getHash().toString();
                String rawParentTxId = sellersRawBtcInputs.get(i).getParentTxId(btcWalletService);
                checkArgument(parentTxId.equals(rawParentTxId),
                        "Spending tx mismatch between sellersBtcInputs and sellersRawBtcInputs at index %s", i);
            }

            boolean hasUnSignedInputs = sellersBtcInputs.stream()
                    .anyMatch(input -> input.getScriptSig() == null && !input.hasWitness());
            checkArgument(!hasUnSignedInputs, "SellersBtcInputs from tx has unsigned inputs");

            long change = request.getBtcChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BTC change must be 0 or above dust");

            long sumInputs = sellersRawBtcInputs.stream().mapToLong(input -> input.value).sum();
            int sellersTxSize = BsqSwapCalculation.getVBytesSize(sellersRawBtcInputs, change);
            long sellersBtcInputAmount = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, getSellersTradeFee()).getValue();
            // It can be that there have been dust change which got added to miner fees, so sumInputs could be a bit larger.
            checkArgument(sumInputs >= sellersBtcInputAmount,
                    "Sellers BTC input amount do not match our calculated required BTC input amount");

            int buyersTxSize = BsqSwapCalculation.getVBytesSize(buyersBsqInputs, protocolModel.getChange());
            long txFeePerVbyte = trade.getTxFeePerVbyte();
            long buyersTxFee = BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, buyersTxSize, getBuyersTradeFee());
            long sellersTxFee = BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, sellersTxSize, getSellersTradeFee());
            long buyersBtcPayout = protocolModel.getPayout();
            long expectedChange = sumInputs - buyersBtcPayout - sellersTxFee - buyersTxFee;
            boolean isChangeAboveDust = Restrictions.isAboveDust(Coin.valueOf(expectedChange));
            if (expectedChange != change && isChangeAboveDust) {
                log.warn("Sellers BTC change is not as expected. This can happen if fee estimation for buyersBsqInputs did not " +
                        "succeed (e.g. dust change, max. iterations reached,...");
                log.warn("buyersBtcPayout={}, sumInputs={}, sellersTxFee={}, buyersTxFee={}, expectedChange={}, change={}",
                        buyersBtcPayout, sumInputs, sellersTxFee, buyersTxFee, expectedChange, change);
            }
            // By enforcing that it must not be larger than expectedChange we guarantee that peer did not cheat on
            // tx fees.
            checkArgument(change <= expectedChange,
                    "Change must be smaller or equal to expectedChange");

            NetworkParameters params = btcWalletService.getParams();
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
            tradePeer.setInputs(sellersRawBtcInputs);
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
