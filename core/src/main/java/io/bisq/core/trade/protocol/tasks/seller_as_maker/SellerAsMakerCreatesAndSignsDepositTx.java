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

package io.bisq.core.trade.protocol.tasks.seller_as_maker;

import io.bisq.common.crypto.Hash;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.data.PreparedDepositTxAndMakerInputs;
import io.bisq.core.btc.data.RawTransactionInput;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.offer.Offer;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import lombok.extern.slf4j.Slf4j;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class SellerAsMakerCreatesAndSignsDepositTx extends TradeTask {
    @SuppressWarnings({"WeakerAccess", "unused"})
    public SellerAsMakerCreatesAndSignsDepositTx(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");

            BtcWalletService walletService = processModel.getBtcWalletService();
            String id = processModel.getOffer().getId();
            TradingPeer tradingPeer = processModel.getTradingPeer();
            final Offer offer = trade.getOffer();

            // params
            final boolean makerIsBuyer = false;

            final byte[] contractHash = Hash.getSha256Hash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");

            final Coin makerInputAmount = offer.getSellerSecurityDeposit().add(trade.getTradeAmount());
            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry makerMultiSigAddressEntry = addressEntryOptional.get();
            makerMultiSigAddressEntry.setCoinLockedInMultiSig(makerInputAmount);
            walletService.saveAddressEntryList();

            final Coin msOutputAmount = makerInputAmount
                    .add(trade.getTxFee())
                    .add(offer.getBuyerSecurityDeposit());

            final List<RawTransactionInput> takerRawTransactionInputs = tradingPeer.getRawTransactionInputs();
            final long takerChangeOutputValue = tradingPeer.getChangeOutputValue();
            final String takerChangeAddressString = tradingPeer.getChangeOutputAddress();
            final Address makerAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            final Address makerChangeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            final byte[] buyerPubKey = tradingPeer.getMultiSigPubKey();
            final byte[] sellerPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(sellerPubKey,
                            makerMultiSigAddressEntry.getPubKey()),
                    "sellerPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            final byte[] arbitratorBtcPubKey = trade.getArbitratorBtcPubKey();

            PreparedDepositTxAndMakerInputs result = processModel.getTradeWalletService().makerCreatesAndSignsDepositTx(
                    makerIsBuyer,
                    contractHash,
                    makerInputAmount,
                    msOutputAmount,
                    takerRawTransactionInputs,
                    takerChangeOutputValue,
                    takerChangeAddressString,
                    makerAddress,
                    makerChangeAddress,
                    buyerPubKey,
                    sellerPubKey,
                    arbitratorBtcPubKey);

            processModel.setPreparedDepositTx(result.depositTransaction);
            processModel.setRawTransactionInputs(result.rawMakerInputs);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
