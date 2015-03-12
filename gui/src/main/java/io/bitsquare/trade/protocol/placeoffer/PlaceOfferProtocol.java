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

package io.bitsquare.trade.protocol.placeoffer;

import io.bitsquare.trade.handlers.TransactionResultHandler;
import io.bitsquare.trade.protocol.placeoffer.tasks.AddOffer;
import io.bitsquare.trade.protocol.placeoffer.tasks.BroadcastCreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.CreateOfferFeeTx;
import io.bitsquare.trade.protocol.placeoffer.tasks.ValidateOffer;
import io.bitsquare.util.handlers.FaultHandler;
import io.bitsquare.util.tasks.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceOfferProtocol {
    private static final Logger log = LoggerFactory.getLogger(PlaceOfferProtocol.class);

    private final PlaceOfferModel model;
    private TransactionResultHandler resultHandler;
    private FaultHandler faultHandle;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PlaceOfferProtocol(PlaceOfferModel model,
                              TransactionResultHandler resultHandler,
                              FaultHandler faultHandle) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.faultHandle = faultHandle;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void placeOffer() {
        TaskRunner<PlaceOfferModel> sequence = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleRequestTakeOfferMessage completed");
                    resultHandler.handleResult(model.getTransaction());
                },
                (message, throwable) -> {
                    log.error(message);
                    faultHandle.handleFault(message, throwable);
                }
        );
        sequence.addTasks(
                ValidateOffer.class,
                CreateOfferFeeTx.class,
                BroadcastCreateOfferFeeTx.class,
                AddOffer.class
        );
        sequence.run();
    }
}
