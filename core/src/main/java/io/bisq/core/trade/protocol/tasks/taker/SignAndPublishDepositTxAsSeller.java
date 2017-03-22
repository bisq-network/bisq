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

package io.bisq.core.trade.protocol.tasks.taker;

import com.google.common.util.concurrent.FutureCallback;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.crypto.Hash;
import io.bisq.protobuffer.payload.btc.RawTransactionInput;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;

public class SignAndPublishDepositTxAsSeller extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(SignAndPublishDepositTxAsSeller.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public SignAndPublishDepositTxAsSeller(TaskRunner taskHandler, Trade trade) {
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

            ArrayList<RawTransactionInput> sellerInputs = processModel.getRawTransactionInputs();
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry sellerMultiSigAddressEntry = addressEntryOptional.get();
            byte[] sellerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(sellerMultiSigPubKey,
                            sellerMultiSigAddressEntry.getPubKey()),
                    "sellerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            Coin sellerInput = Coin.valueOf(sellerInputs.stream().mapToLong(input -> input.value).sum());
            sellerMultiSigAddressEntry.setCoinLockedInMultiSig(sellerInput.subtract(trade.getTxFee().multiply(2)));
            TradingPeer tradingPeer = processModel.tradingPeer;
            Transaction depositTx = processModel.getTradeWalletService().takerSignsAndPublishesDepositTx(
                    true,
                    contractHash,
                    processModel.getPreparedDepositTx(),
                    tradingPeer.getRawTransactionInputs(),
                    sellerInputs,
                    tradingPeer.getMultiSigPubKey(),
                    sellerMultiSigPubKey,
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
            trade.setDepositTx(depositTx);
        } catch (Throwable t) {
            failed(t);
        }
    }
}
