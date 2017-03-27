/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.core.trade.protocol.tasks.buyer_as_taker;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.data.InputsAndChangeOutput;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

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
            Coin txFee = trade.getTxFee();
            Coin doubleTxFee = txFee.add(txFee);
            Coin takerInputAmount = trade.getOffer().getBuyerSecurityDeposit().add(doubleTxFee);
            BtcWalletService walletService = processModel.getWalletService();
            Address takersAddress = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address takersChangeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            InputsAndChangeOutput result = processModel.getTradeWalletService().takerCreatesDepositsTxInputs(
                    takerInputAmount,
                    txFee,
                    takersAddress,
                    takersChangeAddress);
            processModel.setRawTransactionInputs(result.rawTransactionInputs);
            processModel.setChangeOutputValue(result.changeOutputValue);
            processModel.setChangeOutputAddress(result.changeOutputAddress);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
