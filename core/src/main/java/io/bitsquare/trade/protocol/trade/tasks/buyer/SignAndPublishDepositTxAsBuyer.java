/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.trade.tasks.buyer;

import com.google.common.util.concurrent.FutureCallback;
import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.btc.WalletService;
import io.bitsquare.btc.data.RawTransactionInput;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradingPeer;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;

public class SignAndPublishDepositTxAsBuyer extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTxAsBuyer.class);

    public SignAndPublishDepositTxAsBuyer(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");


            byte[] contractHash = Hash.getHash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            ArrayList<RawTransactionInput> buyerInputs = processModel.getRawTransactionInputs();
            WalletService walletService = processModel.getWalletService();
            AddressEntry buyerMultiSigAddressEntry = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.MULTI_SIG);
            buyerMultiSigAddressEntry.setLockedTradeAmount(Coin.valueOf(buyerInputs.stream().mapToLong(input -> input.value).sum()).subtract(FeePolicy.getFixedTxFeeForTrades()));
            walletService.saveAddressEntryList();
            TradingPeer tradingPeer = processModel.tradingPeer;
            processModel.getTradeWalletService().takerSignsAndPublishesDepositTx(
                    false,
                    contractHash,
                    processModel.getPreparedDepositTx(),
                    buyerInputs,
                    tradingPeer.getRawTransactionInputs(),
                    buyerMultiSigAddressEntry.getPubKey(),
                    tradingPeer.getMultiSigPubKey(),
                    trade.getArbitratorPubKey(),
                    new FutureCallback<Transaction>() {
                        @Override
                        public void onSuccess(Transaction transaction) {
                            log.trace("takerSignAndPublishTx succeeded " + transaction);

                            trade.setDepositTx(transaction);
                            trade.setState(Trade.State.TAKER_PUBLISHED_DEPOSIT_TX);

                            complete();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable t) {
                            failed(t);
                        }
                    });
        } catch (Throwable t) {
            failed(t);
        }
    }
}
