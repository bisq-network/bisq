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

package bisq.core.trade.protocol.bsq_swap.tasks.seller;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsq_swap.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;
import bisq.core.trade.protocol.messages.bsq_swap.TxInputsMessage;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class ProcessTxInputsMessage extends BsqSwapTask {
    public ProcessTxInputsMessage(TaskRunner<BsqSwapTrade> taskHandler, BsqSwapTrade bsqSwapTrade) {
        super(taskHandler, bsqSwapTrade);
    }

    @Override
    protected void run() {
        try {
            TxInputsMessage message = checkNotNull((TxInputsMessage) protocolModel.getTradeMessage());
            checkNotNull(message);

            List<RawTransactionInput> inputs = message.getBsqInputs();
            checkArgument(!inputs.isEmpty(), "Buyers BSQ inputs must not be empty");

            long sumInputs = inputs.stream().mapToLong(rawTransactionInput -> rawTransactionInput.value).sum();
            long buyersBsqInputAmount = BsqSwapCalculation.getBuyersBsqInputValue(trade, getBuyersTradeFee()).getValue();
            checkArgument(sumInputs >= buyersBsqInputAmount,
                    "Buyers BSQ input amount do not match our calculated required BSQ input amount");

            DaoFacade daoFacade = protocolModel.getDaoFacade();
            long numValidBsqInputs = inputs.stream()
                    .map(input -> new TxOutputKey(txIdOfRawInputParentTx(input), (int) input.index))
                    .filter(daoFacade::isTxOutputSpendable)
                    .count();
            checkArgument(inputs.size() == numValidBsqInputs,
                    "Some of the buyers BSQ inputs are not from spendable BSQ utxo's according to our DAO state data.");

            long change = message.getBsqChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BSQ change must be 0 or above dust");

            Coin sellersBsqPayoutAmount = BsqSwapCalculation.getSellerBsqPayoutValue(trade, getSellersTradeFee());
            protocolModel.setPayout(sellersBsqPayoutAmount.getValue());

            long expectedChange = sumInputs - sellersBsqPayoutAmount.getValue() - getBuyersTradeFee() - getSellersTradeFee();
            if (expectedChange != change) {
                log.warn("Buyers BSQ change is not as expected. This can happen if change would be below dust. " +
                        "The change would be used as miner fee in such cases.");
                log.warn("sellersBsqPayoutAmount={}, sumInputs={}, getBuyersTradeFee={}, " +
                                "getSellersTradeFee={}, expectedChange={},change={}",
                        sellersBsqPayoutAmount.value, sumInputs, getBuyersTradeFee(),
                        getSellersTradeFee(), expectedChange, change);
            }
            checkArgument(change <= expectedChange,
                    "Change must be smaller or equal to expectedChange");

            NetworkParameters params = protocolModel.getBtcWalletService().getParams();
            String buyersBtcPayoutAddress = message.getBuyersBtcPayoutAddress();
            checkNotNull(buyersBtcPayoutAddress, "buyersBtcPayoutAddress must not be null");
            checkArgument(!buyersBtcPayoutAddress.isEmpty(), "buyersBtcPayoutAddress must not be empty");
            Address.fromString(params, buyersBtcPayoutAddress); // If address is not a BTC address it throws an exception

            String buyersBsqChangeAddress = message.getBuyersBsqChangeAddress();
            checkNotNull(buyersBsqChangeAddress, "buyersBsqChangeAddress must not be null");
            checkArgument(!buyersBsqChangeAddress.isEmpty(), "buyersBsqChangeAddress must not be empty");
            Address.fromString(params, buyersBsqChangeAddress); // If address is not a BTC address it throws an exception

            // Apply data
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            tradePeer.setInputs(inputs);
            tradePeer.setChange(change);
            tradePeer.setBtcAddress(buyersBtcPayoutAddress);
            tradePeer.setBsqAddress(buyersBsqChangeAddress);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

    private String txIdOfRawInputParentTx(RawTransactionInput input) {
        return protocolModel.getBtcWalletService().getTxFromSerializedTx(input.parentTransaction).getTxId().toString();
    }

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
