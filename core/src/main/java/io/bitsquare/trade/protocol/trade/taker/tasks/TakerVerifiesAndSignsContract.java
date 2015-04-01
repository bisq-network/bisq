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

package io.bitsquare.trade.protocol.trade.taker.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.TakerTrade;
import io.bitsquare.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TakerVerifiesAndSignsContract extends TakerTradeTask {
    private static final Logger log = LoggerFactory.getLogger(TakerVerifiesAndSignsContract.class);

    public TakerVerifiesAndSignsContract(TaskRunner taskHandler, TakerTrade takerTrade) {
        super(taskHandler, takerTrade);
    }

    @Override
    protected void doRun() {
        try {
            Contract contract = new Contract(
                    takerTradeProcessModel.getOffer(),
                    takerTrade.getTradeAmount(),
                    takerTradeProcessModel.getTakeOfferFeeTx().getHashAsString(),
                    takerTradeProcessModel.offerer.getAccountId(),
                    takerTradeProcessModel.taker.getAccountId(),
                    takerTradeProcessModel.offerer.getFiatAccount(),
                    takerTradeProcessModel.taker.getFiatAccount(),
                    takerTradeProcessModel.offerer.getP2pSigPubKey(),
                    takerTradeProcessModel.taker.getP2pSigPubKey());
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = takerTradeProcessModel.getSignatureService().signMessage(takerTradeProcessModel.taker.getRegistrationKeyPair(),
                    contractAsJson);

            takerTrade.setContract(contract);
            takerTrade.setContractAsJson(contractAsJson);
            takerTrade.setOffererContractSignature(signature);
            takerTrade.setOffererContractSignature(takerTradeProcessModel.offerer.getContractSignature());

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            takerTrade.setThrowable(t);

            failed(t);
        }
    }
}
