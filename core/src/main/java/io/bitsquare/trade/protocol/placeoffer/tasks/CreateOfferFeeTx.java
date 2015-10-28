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

package io.bitsquare.trade.protocol.placeoffer.tasks;

import io.bitsquare.arbitration.Arbitrator;
import io.bitsquare.btc.FeePolicy;
import io.bitsquare.common.taskrunner.Task;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.p2p.Address;
import io.bitsquare.trade.protocol.placeoffer.PlaceOfferModel;
import io.bitsquare.trade.protocol.trade.ArbitrationSelectionRule;
import org.bitcoinj.core.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateOfferFeeTx extends Task<PlaceOfferModel> {
    private static final Logger log = LoggerFactory.getLogger(CreateOfferFeeTx.class);

    public CreateOfferFeeTx(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            Address selectedArbitratorAddress = ArbitrationSelectionRule.select(model.user.getAcceptedArbitratorAddresses(), model.offer);
            log.debug("selectedArbitratorAddress " + selectedArbitratorAddress);
            Arbitrator selectedArbitrator = model.user.getAcceptedArbitratorByAddress(selectedArbitratorAddress);
            Transaction transaction = model.tradeWalletService.createTradingFeeTx(
                    model.walletService.getAddressEntryByOfferId(model.offer.getId()),
                    FeePolicy.CREATE_OFFER_FEE,
                    selectedArbitrator.getBtcAddress());

            // We assume there will be no tx malleability. We add a check later in case the published offer has a different hash.
            // As the txId is part of the offer and therefore change the hash data we need to be sure to have no
            // tx malleability
            model.offer.setOfferFeePaymentTxID(transaction.getHashAsString());
            model.setTransaction(transaction);

            complete();
        } catch (Throwable t) {
            model.offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());
            failed(t);
        }
    }
}
