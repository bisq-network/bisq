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

package io.bisq.core.offer.availability.tasks;

import io.bisq.common.taskrunner.Task;
import io.bisq.common.taskrunner.TaskRunner;
import io.bisq.core.offer.AvailabilityResult;
import io.bisq.core.offer.Offer;
import io.bisq.core.offer.availability.OfferAvailabilityModel;
import io.bisq.core.offer.messages.OfferAvailabilityResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProcessOfferAvailabilityResponse extends Task<OfferAvailabilityModel> {
    private static final Logger log = LoggerFactory.getLogger(ProcessOfferAvailabilityResponse.class);

    public ProcessOfferAvailabilityResponse(TaskRunner taskHandler, OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();
            OfferAvailabilityResponse offerAvailabilityResponse = model.getMessage();

            if (model.offer.getState() != Offer.State.REMOVED) {
                if (offerAvailabilityResponse.getAvailabilityResult() == AvailabilityResult.AVAILABLE) {
                    model.offer.setState(Offer.State.AVAILABLE);
                } else {
                    model.offer.setState(Offer.State.NOT_AVAILABLE);
                    failed("Take offer attempt rejected because of: " + offerAvailabilityResponse.getAvailabilityResult());
                }
            }

            complete();
        } catch (Throwable t) {
            model.offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
