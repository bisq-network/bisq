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
import bisq.core.offer.availability.ArbitratorSelection;
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.messages.OfferAvailabilityResponse;
import bisq.core.trade.protocol.ArbitratorSelectionRule;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import com.google.common.collect.Lists;

import java.util.List;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ProcessOfferAvailabilityResponse extends Task<OfferAvailabilityModel> {
    public ProcessOfferAvailabilityResponse(TaskRunner taskHandler, OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        Offer offer = model.getOffer();
        try {
            runInterceptHook();
            OfferAvailabilityResponse offerAvailabilityResponse = model.getMessage();

            if (offer.getState() != Offer.State.REMOVED) {
                if (offerAvailabilityResponse.getAvailabilityResult() == AvailabilityResult.AVAILABLE) {
                    offer.setState(Offer.State.AVAILABLE);
                    if (ArbitratorSelection.isNewRuleActivated()) {
                        NodeAddress selectedArbitrator = offerAvailabilityResponse.getArbitrator();
                        if (selectedArbitrator == null) {
                            log.debug("Maker is on old version and does not send the selected arbitrator in the offerAvailabilityResponse. " +
                                    "We use the old selection model instead with the supported arbitrators of the  offers");
                            List<NodeAddress> acceptedArbitratorAddresses = model.getUser().getAcceptedArbitratorAddresses();
                            log.error("acceptedArbitratorAddresses " + acceptedArbitratorAddresses);
                            if (acceptedArbitratorAddresses != null) {
                                try {
                                    model.setSelectedArbitrator(ArbitratorSelectionRule.select(Lists.newArrayList(acceptedArbitratorAddresses), offer));
                                } catch (Throwable t) {
                                    failed("There is no arbitrator matching that offer. The maker has " +
                                            "not updated to the latest version and the arbitrators selected for that offer are not available anymore.");
                                }
                            } else {
                                failed("There is no arbitrator available.");
                            }
                        } else {
                            model.setSelectedArbitrator(selectedArbitrator);
                        }
                    }
                } else {
                    offer.setState(Offer.State.NOT_AVAILABLE);
                    failed("Take offer attempt rejected because of: " + offerAvailabilityResponse.getAvailabilityResult());
                }
            }

            complete();
        } catch (Throwable t) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
