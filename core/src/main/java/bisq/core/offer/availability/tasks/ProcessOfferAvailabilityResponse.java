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

package bisq.core.offer.availability.tasks;

import bisq.core.offer.AvailabilityResult;
import bisq.core.offer.Offer;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.messages.OfferAvailabilityResponse;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

public class ProcessOfferAvailabilityResponse extends Task<OfferAvailabilityModel> {
    public ProcessOfferAvailabilityResponse(TaskRunner taskHandler, OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            OfferAvailabilityResponse offerAvailabilityResponse = model.getMessage();

            if (model.getOffer().getState() != Offer.State.REMOVED) {
                if (offerAvailabilityResponse.getAvailabilityResult() == AvailabilityResult.AVAILABLE) {
                    model.getOffer().setState(Offer.State.AVAILABLE);
                } else {
                    model.getOffer().setState(Offer.State.NOT_AVAILABLE);
                    failed("Take offer attempt rejected because of: " + offerAvailabilityResponse.getAvailabilityResult());
                }
            }

            complete();
        } catch (Throwable t) {
            model.getOffer().setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
