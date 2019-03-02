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

package bisq.core.trade.protocol.tasks.buyer_as_taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.InputsAndChangeOutput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.trade.Trade;
import bisq.core.trade.protocol.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BuyerAsTakerCreatesDepositTxInputs extends TradeTask {

    @SuppressWarnings({"WeakerAccess", "unused"})
    public BuyerAsTakerCreatesDepositTxInputs(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            // In case we pay the taker fee in bsq we reduce tx fee by that as the burned bsq satoshis goes to miners.
            Coin bsqTakerFee = trade.isCurrencyForTakerFeeBtc() ? Coin.ZERO : trade.getTakerFee();

            Coin txFee = trade.getTxFee();
            Coin takerInputAmount = trade.getOffer().getBuyerSecurityDeposit().add(txFee).add(txFee).subtract(bsqTakerFee);
            BtcWalletService walletService = processModel.getBtcWalletService();
            Address takersAddress = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(),
                    AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            InputsAndChangeOutput result = processModel.getTradeWalletService().takerCreatesDepositsTxInputs(
                    processModel.getTakeOfferFeeTx(),
                    takerInputAmount,
                    txFee.subtract(bsqTakerFee),
                    takersAddress);
            processModel.setRawTransactionInputs(result.rawTransactionInputs);
            processModel.setChangeOutputValue(result.changeOutputValue);
            processModel.setChangeOutputAddress(result.changeOutputAddress);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
