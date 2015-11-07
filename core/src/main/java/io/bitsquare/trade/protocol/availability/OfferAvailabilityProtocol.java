/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.trade.protocol.availability;

import io.bitsquare.common.handlers.ErrorMessageHandler;
import io.bitsquare.common.handlers.ResultHandler;
import io.bitsquare.common.taskrunner.TaskRunner;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.messaging.DecryptedMailListener;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.messages.OfferMessage;
import io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.tasks.SendOfferAvailabilityRequest;
import org.reactfx.util.FxTimer;
import org.reactfx.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class OfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(OfferAvailabilityProtocol.class);

    private static final long TIMEOUT = 30 * 1000;

    private final OfferAvailabilityModel model;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private final DecryptedMailListener decryptedMailListener;
    private Timer timeoutTimer;

    private TaskRunner<OfferAvailabilityModel> taskRunner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferAvailabilityProtocol(OfferAvailabilityModel model, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;

        decryptedMailListener = (decryptedMessageWithPubKey, peerAddress) -> {
            Message message = decryptedMessageWithPubKey.message;
            if (message instanceof OfferMessage) {
                OfferMessage offerMessage = (OfferMessage) message;
                nonEmptyStringOf(offerMessage.offerId);
                if (message instanceof OfferAvailabilityResponse
                        && model.offer.getId().equals(offerMessage.offerId)) {
                    log.trace("handle OfferAvailabilityResponse = " + message.getClass().getSimpleName() + " from " + peerAddress);
                    handle((OfferAvailabilityResponse) message);
                }
            }
        };
    }

    private void cleanup() {
        stopTimeout();
        model.p2PService.removeDecryptedMailListener(decryptedMailListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability() {
        // reset
        model.offer.setState(Offer.State.UNDEFINED);

        model.p2PService.addDecryptedMailListener(decryptedMailListener);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at onCheckOfferAvailability completed");
                    stopTimeout();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                    stopTimeout();
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(
                GetPeerAddress.class,
                SendOfferAvailabilityRequest.class
        );
        startTimeout();
        taskRunner.run();
    }

    public void cancel() {
        taskRunner.cancel();
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(OfferAvailabilityResponse message) {
        stopTimeout();
        startTimeout();
        model.setMessage(message);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handle OfferAvailabilityResponse completed");
                    stopTimeout();
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                    stopTimeout();
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessOfferAvailabilityResponse.class);
        taskRunner.run();
    }

    private void startTimeout() {
        stopTimeout();

        timeoutTimer = FxTimer.runLater(Duration.ofMillis(TIMEOUT), () -> {
            Utilities.setThreadName("OfferAvailabilityProtocol:Timeout");
            log.warn("Timeout reached");
            errorMessageHandler.handleErrorMessage("Timeout reached: Peer has not responded.");
        });
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }
}
