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

package io.bisq.trade.protocol.tasks.seller;

import io.bisq.btc.AddressEntry;
import io.bisq.btc.data.InputsAndChangeOutput;
import io.bisq.btc.wallet.BtcWalletService;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.trade.Trade;
import io.bisq.trade.protocol.tasks.TradeTask;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerCreatesDepositTxInputsAsSeller extends TradeTask {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(TakerCreatesDepositTxInputsAsSeller.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public TakerCreatesDepositTxInputsAsSeller(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            if (trade.getTradeAmount() != null) {
                Coin txFee = trade.getTxFee();
                Coin doubleTxFee = txFee.add(txFee);
                Coin takerInputAmount = trade.getOffer().getSellerSecurityDeposit()
                        .add(doubleTxFee).add(trade.getTradeAmount());

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
            } else {
                failed("trade.getTradeAmount() = null");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
