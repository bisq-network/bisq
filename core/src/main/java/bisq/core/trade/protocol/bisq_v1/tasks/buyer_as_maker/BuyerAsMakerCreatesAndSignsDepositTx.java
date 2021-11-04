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

package bisq.core.trade.protocol.bisq_v1.tasks.buyer_as_maker;

import bisq.core.btc.model.AddressEntry;
import bisq.core.btc.model.PreparedDepositTxAndMakerInputs;
import bisq.core.btc.model.RawTransactionInput;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.offer.Offer;
import bisq.core.trade.model.bisq_v1.Trade;
import bisq.core.trade.protocol.bisq_v1.model.TradingPeer;
import bisq.core.trade.protocol.bisq_v1.tasks.TradeTask;

import bisq.common.taskrunner.TaskRunner;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class BuyerAsMakerCreatesAndSignsDepositTx extends TradeTask {
    public BuyerAsMakerCreatesAndSignsDepositTx(TaskRunner<Trade> taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            Coin tradeAmount = checkNotNull(trade.getAmount(), "trade.getTradeAmount() must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            TradingPeer tradingPeer = processModel.getTradePeer();
            Offer offer = checkNotNull(trade.getOffer());

            Coin makerInputAmount = offer.getBuyerSecurityDeposit();
            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry makerMultiSigAddressEntry = addressEntryOptional.get();
            processModel.getBtcWalletService().setCoinLockedInMultiSigAddressEntry(makerMultiSigAddressEntry, makerInputAmount.value);
            walletService.saveAddressEntryList();

            Coin msOutputAmount = makerInputAmount
                    .add(trade.getTradeTxFee())
                    .add(offer.getSellerSecurityDeposit())
                    .add(tradeAmount);

            List<RawTransactionInput> takerRawTransactionInputs = checkNotNull(tradingPeer.getRawTransactionInputs());
            checkArgument(takerRawTransactionInputs.stream().allMatch(processModel.getTradeWalletService()::isP2WH),
                    "all takerRawTransactionInputs must be P2WH");
            long takerChangeOutputValue = tradingPeer.getChangeOutputValue();
            @Nullable String takerChangeAddressString = tradingPeer.getChangeOutputAddress();
            Address makerAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address makerChangeAddress = walletService.getFreshAddressEntry().getAddress();
            byte[] buyerPubKey = processModel.getMyMultiSigPubKey();
            byte[] sellerPubKey = checkNotNull(tradingPeer.getMultiSigPubKey());
            checkArgument(Arrays.equals(buyerPubKey,
                    makerMultiSigAddressEntry.getPubKey()),
                    "buyerPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            PreparedDepositTxAndMakerInputs result = processModel.getTradeWalletService().buyerAsMakerCreatesAndSignsDepositTx(
                    makerInputAmount,
                    msOutputAmount,
                    takerRawTransactionInputs,
                    takerChangeOutputValue,
                    takerChangeAddressString,
                    makerAddress,
                    makerChangeAddress,
                    buyerPubKey,
                    sellerPubKey);

            processModel.setPreparedDepositTx(result.depositTransaction);
            processModel.setRawTransactionInputs(result.rawMakerInputs);

            processModel.getTradeManager().requestPersistence();

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
