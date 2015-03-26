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

package io.bitsquare.trade.protocol.trade.offerer.tasks;

import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.trade.Contract;
import io.bitsquare.trade.OffererTrade;
import io.bitsquare.util.Utilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VerifyAndSignContract extends OffererTradeTask {
    private static final Logger log = LoggerFactory.getLogger(VerifyAndSignContract.class);

    public VerifyAndSignContract(TaskRunner taskHandler, OffererTrade offererTradeProcessModel) {
        super(taskHandler, offererTradeProcessModel);
    }

    @Override
    protected void doRun() {
        try {
            Contract contract = new Contract(
                    offererTradeProcessModel.offer,
                    offererTrade.getTradeAmount(),
                    offererTradeProcessModel.getTakeOfferFeeTxId(),
                    offererTradeProcessModel.offerer.accountId,
                    offererTradeProcessModel.taker.accountId,
                    offererTradeProcessModel.offerer.fiatAccount,
                    offererTradeProcessModel.taker.fiatAccount,
                    offererTradeProcessModel.offerer.p2pSigPubKey,
                    offererTradeProcessModel.taker.p2pSigPublicKey);
            String contractAsJson = Utilities.objectToJson(contract);
            String signature = offererTradeProcessModel.signatureService.signMessage(offererTradeProcessModel.offerer.registrationKeyPair, contractAsJson);

            offererTrade.setContract(contract);
            offererTrade.setContractAsJson(contractAsJson);
            offererTrade.setOffererContractSignature(signature);
            offererTrade.setTakerContractSignature(offererTradeProcessModel.taker.contractSignature);

            complete();
        } catch (Throwable t) {
            offererTrade.setThrowable(t);
            offererTrade.setLifeCycleState(OffererTrade.OffererLifeCycleState.OFFER_OPEN);
            failed(t);
        }
    }
}
