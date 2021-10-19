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
import bisq.core.offer.availability.OfferAvailabilityModel;
import bisq.core.offer.availability.messages.OfferAvailabilityRequest;

import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.taskrunner.Task;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SendOfferAvailabilityRequest extends Task<OfferAvailabilityModel> {
    public SendOfferAvailabilityRequest(TaskRunner<OfferAvailabilityModel> taskHandler, OfferAvailabilityModel model) {
        super(taskHandler, model);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            OfferAvailabilityRequest message = new OfferAvailabilityRequest(model.getOffer().getId(),
                    model.getPubKeyRing(), model.getTakersTradePrice(), model.isTakerApiUser());
            log.info("Send {} with offerId {} and uid {} to peer {}",
                    message.getClass().getSimpleName(), message.getOfferId(),
                    message.getUid(), model.getPeerNodeAddress());

            model.getP2PService().sendEncryptedDirectMessage(model.getPeerNodeAddress(),
                    model.getOffer().getPubKeyRing(),
                    message,
                    new SendDirectMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer: offerId={}; uid={}",
                                    message.getClass().getSimpleName(), message.getOfferId(), message.getUid());
                            complete();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("Sending {} failed: uid={}; peer={}; error={}",
                                    message.getClass().getSimpleName(), message.getUid(),
                                    model.getPeerNodeAddress(), errorMessage);
                            model.getOffer().setState(Offer.State.MAKER_OFFLINE);
                        }
                    }
            );
        } catch (Throwable t) {
            model.getOffer().setErrorMessage("An error occurred.\n" +
                    "Error message:\n"
                    + t.getMessage());

            failed(t);
        }
    }
}

