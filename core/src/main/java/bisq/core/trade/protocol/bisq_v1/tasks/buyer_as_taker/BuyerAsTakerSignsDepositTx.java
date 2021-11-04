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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_taker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.crypto.Hash;
import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Transaction;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerAsTakerSignsDepositTx extends TradeTask {

    public BuyerAsTakerSignsDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

           /* log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");*/


            byte[] contractHash = Hash.getSha256Hash(checkNotNull(trade.getContractAsJson()));
            trade.setContractHash(contractHash);
            List<RawTransactionInput> buyerInputs = checkNotNull(processModel.getRawTransactionInputs(), "buyerInputs must not be null");
            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry buyerMultiSigAddressEntry = addressEntryOptional.get();
            Coin buyerInput = Coin.valueOf(buyerInputs.stream().mapToLong(input -> input.value).sum());

            Coin multiSigValue = buyerInput.subtract(trade.getTradeTxFee().multiply(2));
            processModel.getBtcWalletService().setCoinLockedInMultiSigAddressEntry(buyerMultiSigAddressEntry, multiSigValue.value);
            walletService.saveAddressEntryList();

            Offer offer = trade.getOffer();
            Coin msOutputAmount = offer.getBuyerSecurityDeposit().add(offer.getSellerSecurityDeposit()).add(trade.getTradeTxFee())
                    .add(checkNotNull(trade.getAmount()));

            TradingPeer tradingPeer = processModel.getTradePeer();
            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey, buyerMultiSigAddressEntry.getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            List<RawTransactionInput> sellerInputs = checkNotNull(tradingPeer.getRawTransactionInputs());
            byte[] sellerMultiSigPubKey = tradingPeer.getMultiSigPubKey();
            Transaction depositTx = processModel.getTradeWalletService().takerSignsDepositTx(
                    false,
                    processModel.getPreparedDepositTx(),
                    msOutputAmount,
                    buyerInputs,
                    sellerInputs,
                    buyerMultiSigPubKey,
                    sellerMultiSigPubKey);
            processModel.setDepositTx(depositTx);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
