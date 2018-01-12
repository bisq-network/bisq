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

package io.bisq.core.offer.placeoffer.tasks;

import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.offer.placeoffer.PlaceOfferModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AddOfferOfferBook extends Task<PlaceOfferModel> {
    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(AddOfferOfferBook.class);

    @SuppressWarnings({"WeakerAccess", "unused"})
    public AddOfferOfferBook(TaskRunner taskHandler, PlaceOfferModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            model.getOfferBookService().addOffer(model.getOffer(),
                    () -> {
                        model.setOfferAddedToOfferBook(true);
                        complete();
                    },
                    errorMessage -> {
                        model.getOffer().setErrorMessage("Could not add offer to offerbook.\n" +
                                "Please check your network connection and try again.");

                        failed(errorMessage);
                    });
        } catch (Throwable t) {
            model.getOffer().setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
