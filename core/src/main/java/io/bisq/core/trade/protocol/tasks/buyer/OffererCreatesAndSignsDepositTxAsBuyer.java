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

package io.bisq.core.trade.protocol.tasks.buyer;

import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.data.PreparedDepositTxAndOffererInputs;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.crypto.Hash;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class OffererCreatesAndSignsDepositTxAsBuyer extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignsDepositTxAsBuyer.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public OffererCreatesAndSignsDepositTxAsBuyer(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTradeAmount(), "trade.getTradeAmount() must not be null");

            Coin buyerInputAmount = trade.getOffer().getBuyerSecurityDeposit();
            Coin msOutputAmount = buyerInputAmount
                    .add(trade.getTxFee())
                    .add(trade.getOffer().getSellerSecurityDeposit())
                    .add(trade.getTradeAmount());

            log.debug("\n\n------------------------------------------------------------\n"
                    + "Contract as json\n"
                    + trade.getContractAsJson()
                    + "\n------------------------------------------------------------\n");

            byte[] contractHash = Hash.getHash(trade.getContractAsJson());
            trade.setContractHash(contractHash);
            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();

            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry buyerMultiSigAddressEntry = addressEntryOptional.get();
            buyerMultiSigAddressEntry.setCoinLockedInMultiSig(buyerInputAmount);
            walletService.saveAddressEntryList();
            Address offererAddress = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.RESERVED_FOR_TRADE).getAddress();
            Address offererChangeAddress = walletService.getOrCreateAddressEntry(AddressEntry.Context.AVAILABLE).getAddress();
            TradingPeer tradingPeer = processModel.tradingPeer;
            byte[] buyerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(buyerMultiSigPubKey, buyerMultiSigAddressEntry.getPubKey()),
                    "buyerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);
            PreparedDepositTxAndOffererInputs result = processModel.getTradeWalletService().offererCreatesAndSignsDepositTx(
                    true,
                    contractHash,
                    buyerInputAmount,
                    msOutputAmount,
                    tradingPeer.getRawTransactionInputs(),
                    tradingPeer.getChangeOutputValue(),
                    tradingPeer.getChangeOutputAddress(),
                    offererAddress,
                    offererChangeAddress,
                    buyerMultiSigPubKey,
                    tradingPeer.getMultiSigPubKey(),
                    trade.getArbitratorPubKey());

            processModel.setPreparedDepositTx(result.depositTransaction);
            processModel.setRawTransactionInputs(result.rawOffererInputs);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
