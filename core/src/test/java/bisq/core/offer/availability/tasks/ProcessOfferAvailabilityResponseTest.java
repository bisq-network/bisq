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
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;

import bisq.common.taskrunner.TaskRunner;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import static bisq.core.offer.OfferMaker.btcUsdOffer;
import static com.natpryce.makeiteasy.MakeItEasy.make;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

public class ProcessOfferAvailabilityResponseTest {

    @Test
    public void aStateListenerSeesTheReasonOfTheRefusal() {
        Offer offer = make(btcUsdOffer);
        AtomicReference<AvailabilityResult> reasonAtNotAvailable = new AtomicReference<>();
        offer.stateProperty().addListener((observable, oldValue, state) -> {
            if (state == Offer.State.NOT_AVAILABLE)
                reasonAtNotAvailable.set(offer.getAvailabilityResult());
        });

        process(offer, response(offer, AvailabilityResult.PRICE_OUT_OF_TOLERANCE));

        assertEquals(AvailabilityResult.PRICE_OUT_OF_TOLERANCE, reasonAtNotAvailable.get());
    }

    @Test
    public void anAnswerThisVersionDoesNotKnowIsAnUnknownFailure() {
        Offer offer = make(btcUsdOffer);
        protobuf.OfferAvailabilityResponse proto = protobuf.OfferAvailabilityResponse.newBuilder()
                .setOfferId(offer.getId())
                .setAvailabilityResultValue(9999)
                .build();

        process(offer, OfferAvailabilityResponse.fromProto(proto, 0));

        assertEquals(Offer.State.NOT_AVAILABLE, offer.getState());
        assertEquals(AvailabilityResult.UNKNOWN_FAILURE, offer.getAvailabilityResult());
    }

    @Test
    public void aNewRequestClearsTheReasonOfThePreviousAnswer() {
        Offer offer = make(btcUsdOffer);
        process(offer, response(offer, AvailabilityResult.USER_IGNORED));

        // Every availability request starts from this state
        offer.setState(Offer.State.UNKNOWN);

        assertNull(offer.getAvailabilityResult());
    }

    private static OfferAvailabilityResponse response(Offer offer, AvailabilityResult availabilityResult) {
        return new OfferAvailabilityResponse(offer.getId(), availabilityResult, null, null, null);
    }

    @SuppressWarnings("unchecked")
    private static void process(Offer offer, OfferAvailabilityResponse response) {
        OfferAvailabilityModel model = new OfferAvailabilityModel(offer, null, null, null, null, null, null, false);
        model.setMessage(response);
        new ProcessOfferAvailabilityResponse(mock(TaskRunner.class), model).run();
    }
}
