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

package bisq.core.offer.placeoffer.bsq_swap;

import bisq.core.offer.placeoffer.bsq_swap.tasks.AddBsqSwapOfferToOfferBook;
import bisq.core.offer.placeoffer.bsq_swap.tasks.ValidateBsqSwapOffer;

import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.taskrunner.TaskRunner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PlaceBsqSwapOfferProtocol {
    private static final Logger log = LoggerFactory.getLogger(PlaceBsqSwapOfferProtocol.class);

    private final PlaceBsqSwapOfferModel model;
    private final Runnable resultHandler;
    private final ErrorMessageHandler errorMessageHandler;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public PlaceBsqSwapOfferProtocol(PlaceBsqSwapOfferModel model,
                                     Runnable resultHandler,
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
        TaskRunner<PlaceBsqSwapOfferModel> taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleRequestTakeOfferMessage completed");
                    resultHandler.run();
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
                ValidateBsqSwapOffer.class,
                AddBsqSwapOfferToOfferBook.class
        );

        taskRunner.run();
    }
}
