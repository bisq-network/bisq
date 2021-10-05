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

package bisq.core.trade.protocol.bsqswap.tasks.seller;

import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.trade.messages.bsqswap.TxInputsMessage;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.bsqswap.BsqSwapCalculation;
import bisq.core.trade.protocol.bsqswap.BsqSwapTradePeer;
import bisq.core.trade.protocol.bsqswap.tasks.BsqSwapTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;

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
            runInterceptHook();

            TxInputsMessage data = checkNotNull((TxInputsMessage) protocolModel.getTradeMessage());
            checkNotNull(data);

            List<RawTransactionInput> inputs = data.getBsqInputs();
            checkArgument(!inputs.isEmpty(), "inputs must not be empty");
            long sumInputs = inputs.stream().mapToLong(rawTransactionInput -> rawTransactionInput.value).sum();
            // If taker is buyer the inputs are BSQ, otherwise BTC
            checkArgument(sumInputs >= BsqSwapCalculation.getBuyersRequiredBsqInputs(trade).getValue(),
                    "Buyers BSQ inputs are not sufficient");

            long change = data.getBsqChange();
            checkArgument(change == 0 || Restrictions.isAboveDust(Coin.valueOf(change)),
                    "BSQ change must be 0 or above dust");

            String buyersBtcAPayoutAddress = data.getBuyersBtcPayoutAddress();
            checkNotNull(buyersBtcAPayoutAddress, "buyersBtcAPayoutAddress must not be null");
            checkArgument(!buyersBtcAPayoutAddress.isEmpty(), "buyersBtcAPayoutAddress must not be empty");

            String buyersBsqChangeAddress = data.getBuyersBsqChangeAddress();
            checkNotNull(buyersBsqChangeAddress, "buyersBsqChangeAddress must not be null");
            checkArgument(!buyersBsqChangeAddress.isEmpty(), "buyersBsqChangeAddress must not be empty");

            // Apply data
            BsqSwapTradePeer tradePeer = protocolModel.getTradePeer();
            tradePeer.setInputs(data.getBsqInputs());
            tradePeer.setChange(data.getBsqChange());
            tradePeer.setBtcAddress(data.getBuyersBtcPayoutAddress());
            tradePeer.setBsqAddress(data.getBuyersBsqChangeAddress());

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }

}
