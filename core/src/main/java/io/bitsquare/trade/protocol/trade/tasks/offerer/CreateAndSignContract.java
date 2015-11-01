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

import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
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
            checkNotNull(processModel.getTakeOfferFeeTxId(), "processModel.getTakeOfferFeeTxId() must not be null");

            TradingPeer taker = processModel.tradingPeer;
            PaymentAccountContractData offererPaymentAccountContractData = processModel.getPaymentAccountContractData(trade);
            PaymentAccountContractData takerPaymentAccountContractData = taker.getPaymentAccountContractData();
            boolean isBuyerOffererOrSellerTaker = trade instanceof BuyerAsOffererTrade;

            Address buyerAddress = isBuyerOffererOrSellerTaker ? processModel.getMyAddress() : processModel.getTempTradingPeerAddress();
            Address sellerAddress = isBuyerOffererOrSellerTaker ? processModel.getTempTradingPeerAddress() : processModel.getMyAddress();
            log.debug("isBuyerOffererOrSellerTaker " + isBuyerOffererOrSellerTaker);
            log.debug("buyerAddress " + buyerAddress);
            log.debug("sellerAddress " + sellerAddress);
            Contract contract = new Contract(
                    processModel.getOffer(),
                    trade.getTradeAmount(),
                    processModel.getTakeOfferFeeTxId(),
                    buyerAddress,
                    sellerAddress,
                    trade.getArbitratorAddress(),
                    isBuyerOffererOrSellerTaker,
                    processModel.getAccountId(),
                    taker.getAccountId(),
                    offererPaymentAccountContractData,
                    takerPaymentAccountContractData,
                    processModel.getPubKeyRing(),
                    taker.getPubKeyRing(),
                    processModel.getAddressEntry().getAddressString(),
                    taker.getPayoutAddressString(),
                    processModel.getTradeWalletPubKey(),
                    taker.getTradeWalletPubKey()
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
