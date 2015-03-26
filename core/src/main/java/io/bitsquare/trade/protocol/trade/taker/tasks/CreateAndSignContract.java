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

import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.Trade;
import io.bitsquare.trade.protocol.trade.taker.models.TakerAsSellerModel;
import io.bitsquare.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateAndSignContract extends Task<TakerAsSellerModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateAndSignContract.class);

    public CreateAndSignContract(TaskRunner taskHandler, TakerAsSellerModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void doRun() {
        try {
            Trade trade = model.trade;
            Contract contract = new Contract(
                    model.offer,
                    trade.getTradeAmount(),
                    model.getTakeOfferFeeTx().getHashAsString(),
                    model.offerer.accountId,
                    model.taker.accountId,
                    model.offerer.fiatAccount,
                    model.taker.fiatAccount,
                    model.offer.getP2PSigPubKey(),
                    model.taker.p2pSigPubKey);
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = model.signatureService.signMessage(model.taker.registrationKeyPair, contractAsJson);

            trade.setContract(contract);
            trade.setContractAsJson(contractAsJson);
            trade.setTakerContractSignature(signature);

            complete();
        } catch (Throwable t) {
            model.trade.setThrowable(t);
            failed(t);
        }
    }
}
