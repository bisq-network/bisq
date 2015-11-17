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

package io.bitsquare.trade.protocol.trade.tasks.taker;

import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.payment.PaymentAccountContractData;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.SellerAsTakerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.TradingPeer;
import io.bitsquare.trade.protocol.trade.tasks.TradeTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyAndSignContract extends TradeTask {
    private static final Logger log = LoggerFactory.getLogger(VerifyAndSignContract.class);

    public VerifyAndSignContract(TaskRunner taskHandler, Trade trade) {
        super(taskHandler, trade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            if (processModel.getTakeOfferFeeTx() != null) {
                TradingPeer offerer = processModel.tradingPeer;
                PaymentAccountContractData offererPaymentAccountContractData = offerer.getPaymentAccountContractData();
                PaymentAccountContractData takerPaymentAccountContractData = processModel.getPaymentAccountContractData(trade);

                boolean isBuyerOffererAndSellerTaker = trade instanceof SellerAsTakerTrade;
                Address buyerAddress = isBuyerOffererAndSellerTaker ? processModel.getTempTradingPeerAddress() : processModel.getMyAddress();
                Address sellerAddress = isBuyerOffererAndSellerTaker ? processModel.getMyAddress() : processModel.getTempTradingPeerAddress();
                log.debug("isBuyerOffererAndSellerTaker " + isBuyerOffererAndSellerTaker);
                log.debug("buyerAddress " + buyerAddress);
                log.debug("sellerAddress " + sellerAddress);

                Contract contract = new Contract(
                        processModel.getOffer(),
                        trade.getTradeAmount(),
                        processModel.getTakeOfferFeeTx().getHashAsString(),
                        buyerAddress,
                        sellerAddress,
                        trade.getArbitratorAddress(),
                        isBuyerOffererAndSellerTaker,
                        offerer.getAccountId(),
                        processModel.getAccountId(),
                        offererPaymentAccountContractData,
                        takerPaymentAccountContractData,
                        offerer.getPubKeyRing(),
                        processModel.getPubKeyRing(),
                        offerer.getPayoutAddressString(),
                        processModel.getAddressEntry().getAddressString(),
                        offerer.getTradeWalletPubKey(),
                        processModel.getTradeWalletPubKey()
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
            } else {
                failed("processModel.getTakeOfferFeeTx() = null");
            }
        } catch (Throwable t) {
            failed(t);
        }
    }
}
