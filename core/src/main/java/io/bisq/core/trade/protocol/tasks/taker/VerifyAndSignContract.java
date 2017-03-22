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

import io.bisq.common.crypto.Sig;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.common.util.Utilities;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.trade.SellerAsTakerTrade;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.protocol.TradingPeer;
import io.bisq.core.trade.protocol.tasks.TradeTask;
import io.bisq.protobuffer.payload.p2p.NodeAddress;
import io.bisq.protobuffer.payload.payment.PaymentAccountPayload;
import io.bisq.protobuffer.payload.trade.Contract;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class VerifyAndSignContract extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(VerifyAndSignContract.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public VerifyAndSignContract(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkNotNull(trade.getTakeOfferFeeTxId(), "TakeOfferFeeTxId must not be null");

            TradingPeer offerer = processModel.tradingPeer;
            PaymentAccountPayload offererPaymentAccountPayload = offerer.getPaymentAccountPayload();
            PaymentAccountPayload takerPaymentAccountPayload = processModel.getPaymentAccountPayload(trade);

            boolean isBuyerOffererAndSellerTaker = trade instanceof SellerAsTakerTrade;
            NodeAddress buyerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getTempTradingPeerNodeAddress() : processModel.getMyNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getMyNodeAddress() : processModel.getTempTradingPeerNodeAddress();
            log.debug("isBuyerOffererAndSellerTaker " + isBuyerOffererAndSellerTaker);
            log.debug("buyerAddress " + buyerNodeAddress);
            log.debug("sellerAddress " + sellerNodeAddress);

            BtcWalletService walletService = processModel.getWalletService();
            String id = processModel.getOffer().getId();
            AddressEntry takerPayoutAddressEntry = walletService.getOrCreateAddressEntry(id, AddressEntry.Context.TRADE_PAYOUT);
            String takerPayoutAddressString = takerPayoutAddressEntry.getAddressString();
            Optional<AddressEntry> addressEntryOptional = walletService.getAddressEntry(id, AddressEntry.Context.MULTI_SIG);
            checkArgument(addressEntryOptional.isPresent(), "addressEntryOptional must be present");
            AddressEntry takerMultiSigAddressEntry = addressEntryOptional.get();
            byte[] takerMultiSigPubKey = processModel.getMyMultiSigPubKey();
            checkArgument(Arrays.equals(takerMultiSigPubKey,
                            takerMultiSigAddressEntry.getPubKey()),
                    "takerMultiSigPubKey from AddressEntry must match the one from the trade data. trade id =" + id);

            Contract contract = new Contract(
                    processModel.getOffer().getOfferPayload(),
                    trade.getTradeAmount(),
                    trade.getTradePrice(),
                    trade.getTakeOfferFeeTxId(),
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getArbitratorNodeAddress(),
                    isBuyerOffererAndSellerTaker,
                    offerer.getAccountId(),
                    processModel.getAccountId(),
                    offererPaymentAccountPayload,
                    takerPaymentAccountPayload,
                    offerer.getPubKeyRing(),
                    processModel.getPubKeyRing(),
                    offerer.getPayoutAddressString(),
                    takerPayoutAddressString,
                    offerer.getMultiSigPubKey(),
                    takerMultiSigPubKey
            );
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);
            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setTakerContractSignature(signature);

            try {
                Sig.verify(offerer.getPubKeyRing().getSignaturePubKey(),
                        contractAsJson,
                        offerer.getContractSignature());
            } catch (Throwable t) {
                failed("Signature verification failed. " + t.getMessage());
            }

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
