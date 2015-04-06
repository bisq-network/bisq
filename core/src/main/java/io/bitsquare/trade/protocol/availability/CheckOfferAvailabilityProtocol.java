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
import io.bitsquare.offer.Offer;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.MessageHandler;
import io.bitsquare.p2p.Peer;
import io.bitsquare.trade.protocol.availability.messages.ReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.tasks.GetPeerAddress;
import io.bitsquare.trade.protocol.availability.tasks.ProcessReportOfferAvailabilityMessage;
import io.bitsquare.trade.protocol.availability.tasks.RequestIsOfferAvailable;

import org.bitcoinj.utils.Threading;

import java.util.Timer;
import java.util.TimerTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CheckOfferAvailabilityProtocol {
    private static final Logger log = LoggerFactory.getLogger(CheckOfferAvailabilityProtocol.class);

    private static final long TIMEOUT = 10000;

    private final CheckOfferAvailabilityModel model;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private final MessageHandler messageHandler;
    private Timer timeoutTimer;

    private boolean isCanceled;
    private TaskRunner<CheckOfferAvailabilityModel> taskRunner;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public CheckOfferAvailabilityProtocol(CheckOfferAvailabilityModel model, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;
        messageHandler = this::handleMessage;
    }

    private void cleanup() {
        stopTimeout();
        model.messageService.removeMessageHandler(messageHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void checkOfferAvailability() {
        // reset
        model.offer.setState(Offer.State.UNKNOWN);

        model.messageService.addMessageHandler(messageHandler);

        taskRunner = new TaskRunner<>(model,
                () -> log.debug("sequence at onCheckOfferAvailability completed"),
                log::error
        );
        taskRunner.addTasks(
                GetPeerAddress.class,
                RequestIsOfferAvailable.class
        );
        startTimeout();
        taskRunner.run();
    }

    public void cancel() {
        isCanceled = true;
        taskRunner.cancel();
        cleanup();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message handling
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handleMessage(Message message, @SuppressWarnings("UnusedParameters") Peer sender) {
        if (!isCanceled) {
            if (message instanceof ReportOfferAvailabilityMessage && model.offer.getId().equals(((ReportOfferAvailabilityMessage) message).offerId))
                handle((ReportOfferAvailabilityMessage) message);
        }
    }

    private void handle(ReportOfferAvailabilityMessage message) {
        stopTimeout();
        model.setMessage(message);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    log.debug("sequence at handleReportOfferAvailabilityMessage completed");
                    resultHandler.handleResult();
                },
                (errorMessage) -> {
                    log.error(errorMessage);
                    errorMessageHandler.handleErrorMessage(errorMessage);
                }
        );
        taskRunner.addTasks(ProcessReportOfferAvailabilityMessage.class);
        taskRunner.run();
    }

    protected void startTimeout() {
        log.debug("startTimeout");
        stopTimeout();

        timeoutTimer = new Timer();
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                Threading.USER_THREAD.execute(() -> {
                    log.debug("Timeout reached");
                    errorMessageHandler.handleErrorMessage("Timeout reached: Peer has not responded.");
                    model.offer.setState(Offer.State.OFFERER_OFFLINE);
                });
            }
        };

        timeoutTimer.schedule(task, TIMEOUT);
    }

    protected void stopTimeout() {
        log.debug("stopTimeout");
        if (timeoutTimer != null) {
            timeoutTimer.cancel();
            timeoutTimer = null;
        }
    }
}
