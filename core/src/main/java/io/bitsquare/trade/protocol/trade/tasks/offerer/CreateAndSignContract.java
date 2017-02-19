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

package io.bitsquare.trade.protocol.trade.tasks.offerer;

import io.bitsquare.btc.AddressEntry;
import io.bitsquare.btc.WalletService;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.BuyerAsOffererTrade;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradingPeer;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

public class CreateAndSignContract extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public CreateAndSignContract(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            checkNotNull(trade.getTakeOfferFeeTxId(), "trade.getTakeOfferFeeTxId() must not be null");

            TradingPeer taker = processModel.tradingPeer;
            PaymentAccountContractData offererPaymentAccountContractData = processModel.getPaymentAccountContractData(trade);
            checkNotNull(offererPaymentAccountContractData, "offererPaymentAccountContractData must not be null");
            PaymentAccountContractData takerPaymentAccountContractData = taker.getPaymentAccountContractData();
            boolean isBuyerOffererAndSellerTaker = trade instanceof BuyerAsOffererTrade;

            NodeAddress buyerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getMyNodeAddress() : processModel.getTempTradingPeerNodeAddress();
            NodeAddress sellerNodeAddress = isBuyerOffererAndSellerTaker ? processModel.getTempTradingPeerNodeAddress() : processModel.getMyNodeAddress();
            log.debug("isBuyerOffererAndSellerTaker " + isBuyerOffererAndSellerTaker);
            log.debug("buyerAddress " + buyerNodeAddress);
            log.debug("sellerAddress " + sellerNodeAddress);
            WalletService walletService = processModel.getWalletService();
            AddressEntry takerAddressEntry = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.TRADE_PAYOUT);
            AddressEntry offererAddressEntry = walletService.getOrCreateAddressEntry(processModel.getOffer().getId(), AddressEntry.Context.MULTI_SIG);
            Contract contract = new Contract(
                    processModel.getOffer(),
                    trade.getTradeAmount(),
                    trade.getTradePrice(),
                    trade.getTakeOfferFeeTxId(),
                    buyerNodeAddress,
                    sellerNodeAddress,
                    trade.getArbitratorNodeAddress(),
                    isBuyerOffererAndSellerTaker,
                    processModel.getAccountId(),
                    taker.getAccountId(),
                    offererPaymentAccountContractData,
                    takerPaymentAccountContractData,
                    processModel.getPubKeyRing(),
                    taker.getPubKeyRing(),
                    takerAddressEntry.getAddressString(),
                    taker.getPayoutAddressString(),
                    offererAddressEntry.getPubKey(),
                    taker.getMultiSigPubKey()
            );
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = Sig.sign(processModel.getKeyRing().getSignatureKeyPair().getPrivate(), contractAsJson);

            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setOffererContractSignature(signature);

            complete();
        } catch (Throwable t) {
            failed(t);
        }
    }
}
