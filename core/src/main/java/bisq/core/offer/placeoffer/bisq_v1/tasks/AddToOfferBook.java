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

package bisq.core.offer.placeoffer.bisq_v1.tasks;

import bisq.core.offer.placeoffer.bisq_v1.PlaceOfferModel;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

public class AddToOfferBook extends Task<PlaceOfferModel> {

    public AddToOfferBook(TaskRunner<PlaceOfferModel> taskHandler, PlaceOfferModel model) {
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
