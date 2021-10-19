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

import bisq.core.offer.Offer;
import bisq.core.offer.availability.AvailabilityResult;
import bisq.core.offer.availability.DisputeAgentSelection;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class ProcessOfferAvailabilityResponse extends Task<OfferAvailabilityModel> {
    public ProcessOfferAvailabilityResponse(TaskRunner<OfferAvailabilityModel> taskHandler,
                                            OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.getOffer();
        try {
            runInterceptHook();

            checkArgument(offer.getState() != Offer.State.REMOVED, "Offer state must not be Offer.State.REMOVED");

            OfferAvailabilityResponse offerAvailabilityResponse = model.getMessage();

            if (offerAvailabilityResponse.getAvailabilityResult() != AvailabilityResult.AVAILABLE) {
                offer.setState(Offer.State.NOT_AVAILABLE);
                failed("Take offer attempt rejected because of: " + offerAvailabilityResponse.getAvailabilityResult());
                return;
            }

            offer.setState(Offer.State.AVAILABLE);

            model.setSelectedArbitrator(offerAvailabilityResponse.getArbitrator());

            NodeAddress mediator = offerAvailabilityResponse.getMediator();
            if (mediator == null) {
                // We do not get a mediator from old clients so we need to handle the null case.
                mediator = DisputeAgentSelection.getLeastUsedMediator(model.getTradeStatisticsManager(), model.getMediatorManager()).getNodeAddress();
            }
            model.setSelectedMediator(mediator);

            model.setSelectedRefundAgent(offerAvailabilityResponse.getRefundAgent());

            complete();
        } catch (Throwable t) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
