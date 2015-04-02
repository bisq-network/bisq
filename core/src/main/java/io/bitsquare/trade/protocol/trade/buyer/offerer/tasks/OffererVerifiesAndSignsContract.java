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

package io.bitsquare.trade.protocol.trade.buyer.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.OffererAsBuyerTrade;
import io.bitsquare.trade.OffererAsSellerTrade;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;
import io.bitsquare.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererVerifiesAndSignsContract extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererVerifiesAndSignsContract.class);

    public OffererVerifiesAndSignsContract(TaskRunner taskHandler, Trade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            Contract contract = new Contract(
                    processModel.getOffer(),
                    offererTrade.getTradeAmount(),
                    processModel.getTakeOfferFeeTxId(),
                    processModel.getAccountId(),
                    processModel.tradingPeer.getAccountId(),
                    processModel.getFiatAccount(),
                    processModel.tradingPeer.getFiatAccount(),
                    processModel.getP2pSigPubKey(),
                    processModel.tradingPeer.getP2pSigPubKey());
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = processModel.getSignatureService().signMessage(processModel.getRegistrationKeyPair(),
                    contractAsJson);

            offererTrade.setContract(contract);
            offererTrade.setContractAsJson(contractAsJson);
            offererTrade.setOffererContractSignature(signature);
            offererTrade.setTakerContractSignature(processModel.tradingPeer.getContractSignature());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);

            if (offererTrade instanceof OffererAsBuyerTrade)
                offererTrade.setLifeCycleState(OffererAsBuyerTrade.LifeCycleState.OFFER_OPEN);
            else if (offererTrade instanceof OffererAsSellerTrade)
                offererTrade.setLifeCycleState(OffererAsSellerTrade.LifeCycleState.OFFER_OPEN);

            failed(t);
        }
    }
}
