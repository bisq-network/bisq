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
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.messages.BsqSwapFinalizeTxRequest;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

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

import static bisq.core.trade.validation.TradeValidation.checkTradeId;
import static bisq.core.trade.validation.TransactionValidation.checkInputOutpoints;
import static bisq.core.trade.validation.TransactionValidation.checkInputSignatures;
import static bisq.core.trade.validation.TransactionValidation.checkTransaction;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Each seller input in the tx must spend exactly the output described by the matching RawTransactionInput (parent tx
 * ID and output index). The parent tx ID commits to the outputs of the parent tx, so the values and script types we
 * use for the fee and change checks are those of the outputs which the seller inputs spend. The seller inputs must be
 * validly signed, so that the tx is complete once we have signed our inputs.
 * See docs/specifications/trade/bsq-swap-seller-inputs.md.
 * We cannot verify if the sellers inputs really exist and are unspent as we do not have the blockchain data for it.
 * In that case the tx would never get confirmed.
 * The change output cannot be verified exactly due potential dust values and non-deterministic behaviour of the
 * fee estimation.
 * The important values for our BTC output and our BSQ change output are set already in BuyerCreatesBsqInputsAndChange
 * and are not related to the data provided by the peer.
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
            checkTradeId(protocolModel.getOfferId(), request);

            // We will use only the seller's BTC inputs from the tx. The rest of the tx gets rebuilt from our data and
            // compared in BuyerCreatesAndSignsFinalizedTx.
            byte[] tx = request.getTx();
            WalletService btcWalletService = protocolModel.getBtcWalletService();
            NetworkParameters params = btcWalletService.getParams();
            // Rejects a tx which spends the same outpoint twice, so that no input value can be counted twice.
            Transaction sellersTransaction = checkTransaction(btcWalletService.getTxFromSerializedTx(tx));
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
            // RawTransactionInput.validate checks the value and script type only of the output which the seller
            // describes. Each seller input must spend exactly that output, otherwise the seller could spend a smaller
            // output of the same parent tx and claim the value of a larger one.
            checkInputOutpoints(sellersTransaction, buyersInputSize, sellersRawBtcInputs, params, "seller");

            // The seller must spend BTC only. A seller input which spends a BSQ output changes how the DAO parses the
            // tx. Spending a lockup output, or an unlock output before its lock time, even makes the whole tx invalid
            // for the DAO, which burns the BSQ change of the buyer. A BTC output of a BSQ tx is not in the unspent BSQ
            // outputs of the DAO state and is accepted.
            DaoFacade daoFacade = protocolModel.getDaoFacade();
            checkArgument(daoFacade.isDaoStateReadyAndInSync(), "DAO state is not ready and in sync");
            for (RawTransactionInput input : sellersRawBtcInputs) {
                TxOutputKey key = new TxOutputKey(input.getParentTxId(btcWalletService), (int) input.index);
                checkArgument(!daoFacade.isUnspentTxOutput(key), "Seller input %s spends a BSQ output", key);
            }

            // Without valid signatures of the seller the tx would be incomplete after we signed it, and only the seller
            // could complete it later. The seller's SIGHASH_ALL signatures do not cover the scripts and witnesses of
            // our inputs, so signing our inputs later does not invalidate them.
            checkInputSignatures(sellersTransaction, buyersInputSize, sellersRawBtcInputs, params, "seller");

            long change = request.getBtcChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BTC change must be 0 or above dust");

            Coin sumInputs = sellersRawBtcInputs.stream()
                    .map(input -> Coin.valueOf(input.value))
                    .reduce(Coin.ZERO, Coin::add);
            int sellersTxSize = BsqSwapCalculation.getVBytesSize(sellersRawBtcInputs, change);
            Coin sellersBtcInputAmount = BsqSwapCalculation.getSellersBtcInputValue(trade, sellersTxSize, getSellersTradeFee());
            // It can be that there have been dust change which got added to miner fees, so sumInputs could be a bit larger.
            checkArgument(!sumInputs.isLessThan(sellersBtcInputAmount),
                    "Sellers BTC input amount do not match our calculated required BTC input amount");

            int buyersTxSize = BsqSwapCalculation.getVBytesSize(buyersBsqInputs, protocolModel.getChange());
            long txFeePerVbyte = trade.getTxFeePerVbyte();
            Coin buyersTxFee = Coin.valueOf(BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, buyersTxSize, getBuyersTradeFee()));
            Coin sellersTxFee = Coin.valueOf(BsqSwapCalculation.getAdjustedTxFee(txFeePerVbyte, sellersTxSize, getSellersTradeFee()));
            Coin buyersBtcPayout = Coin.valueOf(protocolModel.getPayout());
            Coin expectedChange = sumInputs
                    .subtract(buyersBtcPayout)
                    .subtract(sellersTxFee)
                    .subtract(buyersTxFee);
            boolean isChangeAboveDust = Restrictions.isAboveDust(expectedChange);
            if (expectedChange.value != change && isChangeAboveDust) {
                log.warn("Sellers BTC change is not as expected. This can happen if fee estimation for buyersBsqInputs did not " +
                        "succeed (e.g. dust change, max. iterations reached,...");
                log.warn("buyersBtcPayout={}, sumInputs={}, sellersTxFee={}, buyersTxFee={}, expectedChange={}, change={}",
                        buyersBtcPayout.getValue(), sumInputs.getValue(), sellersTxFee.getValue(),
                        buyersTxFee.getValue(), expectedChange.getValue(), change);
            }
            // By enforcing that it must not be larger than expectedChange we guarantee that peer did not cheat on
            // tx fees.
            checkArgument(change <= expectedChange.value,
                    "Change must be smaller or equal to expectedChange");

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
