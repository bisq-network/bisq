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
import io.bitsquare.crypto.MessageWithPubKey;
import io.bitsquare.p2p.DecryptedMessageHandler;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.offer.Offer;
import io.bitsquare.trade.protocol.availability.messages.OfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.messages.OfferMessage;
import io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.availability.tasks.ProcessOfferAvailabilityResponse;
import io.bitsquare.trade.protocol.availability.tasks.SendOfferAvailabilityRequest;
import io.bitsquare.util.Utilities;

import java.util.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static io.bitsquare.util.Validator.nonEmptyStringOf;

public class OfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(OfferAvailabilityProtocol.class);

    private static final long TIMEOUT = 10000;

    private final OfferAvailabilityModel model;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private final DecryptedMessageHandler decryptedMessageHandler;
    private Timer timeoutTimer;

    private TaskRunner<OfferAvailabilityModel> taskRunner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferAvailabilityProtocol(OfferAvailabilityModel model, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
        decryptedMessageHandler = this::handleDecryptedMessageWithPubKey;
    }

    private void cleanup() {
        stopTimeout();
        model.messageService.removeDecryptedMessageHandler(decryptedMessageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability() {
        // reset
        model.offer.setState(Offer.State.UNDEFINED);

        model.messageService.addDecryptedMessageHandler(decryptedMessageHandler);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at onCheckOfferAvailability completed");
                    stopTimeout();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                    stopTimeout();
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

    protected void handleDecryptedMessageWithPubKey(MessageWithPubKey messageWithPubKey, Peer sender) {
        Message message = messageWithPubKey.getMessage();
        log.trace("handleNewMessage: message = " + message.getClass().getSimpleName() + " from " + sender);
        if (message instanceof OfferMessage) {
            nonEmptyStringOf(((OfferMessage) message).offerId);
            if (message instanceof OfferAvailabilityResponse && model.offer.getId().equals(((OfferMessage) message).offerId))
                handle((OfferAvailabilityResponse) message);
        }
    }


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

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = Utilities.setTimeout(TIMEOUT, () -> {
            log.warn("Timeout reached");
            errorMessageHandler.handleErrorMessage("Timeout reached: Peer has not responded.");
            model.offer.setState(Offer.State.TIMEOUT);
        });
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
