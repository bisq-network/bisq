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
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.dao.DaoFacade;
import bisq.core.dao.state.model.blockchain.TxOutputKey;
import bisq.core.trade.bsq_swap.BsqSwapCalculation;
import bisq.core.trade.model.bsq_swap.BsqSwapTrade;
import bisq.core.trade.protocol.bsq_swap.messages.TxInputsMessage;
import bisq.core.trade.protocol.bsq_swap.model.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsq_swap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * We verify the BSQ inputs to match our calculation for the required inputs. As we have the BSQ inputs in our
 * DAO state we can verify the inputs if they exist and matching the peers values.
 * The change cannot be verified exactly as there are some scenarios with dust spent to miners which are not reflected
 * by the calculations.
 *
 * The sellersBsqPayoutAmount is calculated here independent of the peers data. The BTC change output will be calculated
 * in SellerCreatesAndSignsTx.
 */
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

            BtcWalletService btcWalletService = protocolModel.getBtcWalletService();

            List<RawTransactionInput> inputs = message.getBsqInputs();
            checkArgument(!inputs.isEmpty(), "Buyers BSQ inputs must not be empty");
            inputs.forEach(input -> input.validate(btcWalletService));

            Coin sumInputs = inputs.stream()
                    .map(input -> Coin.valueOf(input.value))
                    .reduce(Coin.ZERO, Coin::add);
            Coin buyersBsqInputAmount = BsqSwapCalculation.getBuyersBsqInputValue(trade, getBuyersTradeFee());
            checkArgument(!sumInputs.isLessThan(buyersBsqInputAmount),
                    "Buyers BSQ input amount do not match our calculated required BSQ input amount");

            DaoFacade daoFacade = protocolModel.getDaoFacade();
            checkArgument(daoFacade.isDaoStateReadyAndInSync(), "DAO state is not ready and in sync");

            Coin sumValidBsqInputValue = inputs.stream()
                    .map(input -> Coin.valueOf(daoFacade.getUnspentTxOutputValue(
                            new TxOutputKey(input.getParentTxId(btcWalletService), (int) input.index))))
                    .reduce(Coin.ZERO, Coin::add);
            checkArgument(sumInputs.equals(sumValidBsqInputValue),
                    "Buyers BSQ input amount must match input amount from unspentTxOutputMap in DAO state");

            long numValidBsqInputs = inputs.stream()
                    .map(input -> new TxOutputKey(input.getParentTxId(btcWalletService), (int) input.index))
                    .filter(daoFacade::isTxOutputSpendable)
                    .count();
            checkArgument(inputs.size() == numValidBsqInputs,
                    "Some of the buyers BSQ inputs are not from spendable BSQ utxo's according to our DAO state data.");

            long change = message.getBsqChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BSQ change must be 0 or above dust");

            // sellersBsqPayoutAmount is not related to peers inputs but we need it in the following steps so we
            // calculate and set it here.
            Coin sellersBsqPayoutAmount = BsqSwapCalculation.getSellersBsqPayoutValue(trade, getSellersTradeFee());
            // Guard: non-positive or sub-dust payout produces an unbroadcastable / non-standard tx.
            // Reject up front rather than commit BSQ inputs in the publish step for a tx miners will drop.
            checkArgument(Restrictions.isAboveDust(sellersBsqPayoutAmount),
                    "Sellers BSQ payout is non-positive or below dust: %s. Trade-fee likely exceeds BSQ trade amount.",
                    sellersBsqPayoutAmount.getValue());
            protocolModel.setPayout(sellersBsqPayoutAmount.getValue());

            Coin expectedChange = sumInputs
                    .subtract(sellersBsqPayoutAmount)
                    .subtract(Coin.valueOf(getBuyersTradeFee()))
                    .subtract(Coin.valueOf(getSellersTradeFee()));
            if (expectedChange.value != change) {
                log.warn("Buyers BSQ change is not as expected. This can happen if change would be below dust. " +
                        "The change would be used as miner fee in such cases.");
                log.warn("sellersBsqPayoutAmount={}, sumInputs={}, getBuyersTradeFee={}, " +
                                "getSellersTradeFee={}, expectedChange={},change={}",
                        sellersBsqPayoutAmount.getValue(), sumInputs.getValue(), getBuyersTradeFee(),
                        getSellersTradeFee(), expectedChange.getValue(), change);
            }
            // By enforcing that it must not be larger than expectedChange we guarantee that peer did not cheat on
            // trade fees.
            checkArgument(change <= expectedChange.value,
                    "Change must be smaller or equal to expectedChange");

            NetworkParameters params = btcWalletService.getParams();
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

    protected abstract long getBuyersTradeFee();

    protected abstract long getSellersTradeFee();
}
