/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.offer.placeoffer.atomic;

import bisq.core.offer.placeoffer.atomic.tasks.AtomicAddToOfferBook;
import bisq.core.offer.placeoffer.atomic.tasks.AtomicValidateOffer;
import bisq.core.trade.misc.TransactionResultHandler;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AtomicPlaceOfferProtocol {
    private static final Logger log = LoggerFactory.getLogger(AtomicPlaceOfferProtocol.class);

    private final AtomicPlaceOfferModel model;
    private final TransactionResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicPlaceOfferProtocol(AtomicPlaceOfferModel model,
                                    TransactionResultHandler resultHandler,
                                    ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void placeOffer() {
        log.debug("model.offer.id" + model.getOffer().getId());
        TaskRunner<AtomicPlaceOfferModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleRequestTakeOfferMessage completed");
                    resultHandler.handleResult(model.getTransaction());
                },
                (errorMessage) -> {
                    log.error(errorMessage);

                    if (model.isOfferAddedToOfferBook()) {
                        model.getOfferBookService().removeOffer(model.getOffer().getOfferPayloadBase(),
                                () -> {
                                    model.setOfferAddedToOfferBook(false);
                                    log.debug("OfferPayload removed from offer book.");
                                },
                                log::error);
                    }
                    model.getOffer().setErrorMessage(errorMessage);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(
                AtomicValidateOffer.class,
                AtomicAddToOfferBook.class
        );

        taskRunner.run();
    }
}
