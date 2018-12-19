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
import bisq.core.trade.protocol.ArbitratorSelectionRule;

import bisq.network.p2p.NodeAddress;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import com.google.common.collect.Lists;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

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

            checkArgument(offer.getState() != Offer.State.REMOVED, "Offer state must not be Offer.State.REMOVED");

            OfferAvailabilityResponse offerAvailabilityResponse = model.getMessage();

            if (offerAvailabilityResponse.getAvailabilityResult() != AvailabilityResult.AVAILABLE) {
                offer.setState(Offer.State.NOT_AVAILABLE);
                failed("Take offer attempt rejected because of: " + offerAvailabilityResponse.getAvailabilityResult());
                return;
            }

            offer.setState(Offer.State.AVAILABLE);

            NodeAddress selectedArbitrator = offerAvailabilityResponse.getArbitrator();

            if (selectedArbitrator != null) {
                model.setSelectedArbitrator(selectedArbitrator);
                complete();
                return;
            }

            // We have an offer from a maker who runs a pre 0.9 version.
            log.info("Maker has on old version and does not send the selected arbitrator in the offerAvailabilityResponse. " +
                    "We use the old selection model instead with the supported arbitrators of the  offers");

            List<NodeAddress> userArbitratorAddresses = model.getUser().getAcceptedArbitratorAddresses();
            checkNotNull(userArbitratorAddresses, "model.getUser().getAcceptedArbitratorAddresses() must not be null");

            List<NodeAddress> offerArbitratorNodeAddresses = offer.getArbitratorNodeAddresses();

            List<NodeAddress> matchingArbitrators = offerArbitratorNodeAddresses.stream()
                    .filter(userArbitratorAddresses::contains)
                    .collect(Collectors.toList());

            if (!matchingArbitrators.isEmpty()) {
                // We have at least one arbitrator which was used in the offer and is still available.
                try {
                    model.setSelectedArbitrator(ArbitratorSelectionRule.select(Lists.newArrayList(matchingArbitrators), offer));
                    complete();
                } catch (Throwable t) {
                    failed("There is no arbitrator matching that offer. The maker has " +
                            "not updated to the latest version and the arbitrators selected for that offer are not available anymore.");
                }
            } else {
                // If an arbitrator which was selected in the offer from an old version has revoked we would get a failed take-offer attempt
                // with lost trade fees. To avoid that we fail here after 1 week after the new rule is activated.
                // Because one arbitrator need to revoke his application and register new as he gets too many transactions already
                // we need to handle the planned revoke case.
                failed("You cannot take that offer because the maker has not updated to version 0.9.");
            }
        } catch (Throwable t) {
            offer.setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}
