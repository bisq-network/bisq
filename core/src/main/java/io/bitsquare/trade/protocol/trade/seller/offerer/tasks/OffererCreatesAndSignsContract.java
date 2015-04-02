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

package io.bitsquare.trade.protocol.trade.seller.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.offerer.tasks.OffererTradeTask;
import io.bitsquare.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OffererCreatesAndSignsContract extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(OffererCreatesAndSignsContract.class);

    public OffererCreatesAndSignsContract(TaskRunner taskHandler, Trade offererTrade) {
        super(taskHandler, offererTrade);
    }

    @Override
    protected void doRun() {
        try {
            assert processModel.getTakeOfferFeeTxId() != null;
            Contract contract = new Contract(
                    processModel.getOffer(),
                    model.getTradeAmount(),
                    processModel.getTakeOfferFeeTxId(),
                    processModel.getAccountId(),
                    processModel.getAccountId(),
                    processModel.getFiatAccount(),
                    processModel.getFiatAccount(),
                    processModel.getOffer().getP2PSigPubKey(),
                    processModel.getP2pSigPubKey());
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = processModel.getSignatureService().signMessage(processModel.getRegistrationKeyPair(),
                    contractAsJson);

            model.setContract(contract);
            model.setContractAsJson(contractAsJson);
            model.setOffererContractSignature(signature);

            complete();
        } catch (Throwable t) {
            t.printStackTrace();
            offererTrade.setThrowable(t);
            failed(t);
        }
    }
}
