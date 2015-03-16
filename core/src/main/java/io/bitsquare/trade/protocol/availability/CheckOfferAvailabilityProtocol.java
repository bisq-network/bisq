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

import io.bitsquare.network.Message;
import io.bitsquare.network.Peer;
import io.bitsquare.offer.Offer;
import io.bitsquare.trade.handlers.MessageHandler;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.availability.tasks.ProcessReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.tasks.RequestIsOfferAvailable;
import io.bitsquare.util.taskrunner.TaskRunner;

import javafx.application.Platform;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(CheckOfferAvailabilityProtocol.class);

    private final CheckOfferAvailabilityModel model;
    private final MessageHandler messageHandler;

    private boolean isCanceled;
    private TaskRunner<CheckOfferAvailabilityModel> taskRunner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CheckOfferAvailabilityProtocol(CheckOfferAvailabilityModel model) {
        this.model = model;
        messageHandler = this::handleMessage;
    }

    public void cleanup() {
        // Cannot remove listener in same execution cycle, so we delay it
        Platform.runLater(() -> model.getTradeMessageService().removeMessageHandler(messageHandler));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability() {
        model.getTradeMessageService().addMessageHandler(messageHandler);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at onCheckOfferAvailability completed");
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(
                GetPeerAddress.class,
                RequestIsOfferAvailable.class
        );
        taskRunner.run();
    }

    public void cancel() {
        isCanceled = true;
        taskRunner.cancel();
        model.getOffer().setState(Offer.State.UNKNOWN);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, @SuppressWarnings("UnusedParameters") Peer sender) {
        if (!isCanceled) {
            if (message instanceof ReportOfferAvailabilityMessage)
                handleReportOfferAvailabilityMessage((ReportOfferAvailabilityMessage) message);
        }
    }

    private void handleReportOfferAvailabilityMessage(ReportOfferAvailabilityMessage message) {
        model.setMessage(message);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleReportOfferAvailabilityMessage completed");
                    model.getResultHandler().handleResult();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessReportOfferAvailabilityMessage.class);
        taskRunner.run();
    }
}
