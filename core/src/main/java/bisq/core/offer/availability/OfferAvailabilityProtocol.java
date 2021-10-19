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

package bisq.core.offer.availability;

import bisq.core.offer.Offer;
import bisq.core.offer.availability.messages.OfferAvailabilityResponse;
import bisq.core.offer.availability.messages.OfferMessage;
import bisq.core.offer.availability.tasks.ProcessOfferAvailabilityResponse;
import bisq.core.offer.availability.tasks.SendOfferAvailabilityRequest;
import bisq.core.util.Validator;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendDirectMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class OfferAvailabilityProtocol {
    private static final long TIMEOUT = 90;

    private final OfferAvailabilityModel model;
    private final ResultHandler resultHandler;
    private final ErrorMessageHandler errorMessageHandler;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;

    private TaskRunner<OfferAvailabilityModel> taskRunner;
    private Timer timeoutTimer;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public OfferAvailabilityProtocol(OfferAvailabilityModel model, ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        this.model = model;
        this.resultHandler = resultHandler;
        this.errorMessageHandler = errorMessageHandler;

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peersNodeAddress) -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            if (networkEnvelope instanceof OfferMessage) {
                OfferMessage offerMessage = (OfferMessage) networkEnvelope;
                Validator.nonEmptyStringOf(offerMessage.offerId);
                if (networkEnvelope instanceof OfferAvailabilityResponse
                        && model.getOffer().getId().equals(offerMessage.offerId)) {
                    handleOfferAvailabilityResponse((OfferAvailabilityResponse) networkEnvelope, peersNodeAddress);
                }
            }
        };
    }

    private void cleanup() {
        stopTimeout();
        model.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Called from UI
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendOfferAvailabilityRequest() {
        // reset
        model.getOffer().setState(Offer.State.UNKNOWN);

        model.getP2PService().addDecryptedDirectMessageListener(decryptedDirectMessageListener);
        model.setPeerNodeAddress(model.getOffer().getMakerNodeAddress());

        taskRunner = new TaskRunner<>(model,
                () -> handleTaskRunnerSuccess("TaskRunner at sendOfferAvailabilityRequest completed", null),
                errorMessage -> handleTaskRunnerFault(errorMessage, null)
        );
        taskRunner.addTasks(SendOfferAvailabilityRequest.class);
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

    private void handleOfferAvailabilityResponse(OfferAvailabilityResponse message, NodeAddress peersNodeAddress) {
        log.info("Received handleOfferAvailabilityResponse from {} with offerId {} and uid {}",
                peersNodeAddress, message.getOfferId(), message.getUid());

        stopTimeout();
        startTimeout();
        model.setMessage(message);

        taskRunner = new TaskRunner<>(model,
                () -> {
                    handleTaskRunnerSuccess("TaskRunner at handle OfferAvailabilityResponse completed", message);

                    stopTimeout();
                    resultHandler.handleResult();
                },
                errorMessage -> handleTaskRunnerFault(errorMessage, message));
        taskRunner.addTasks(ProcessOfferAvailabilityResponse.class);
        taskRunner.run();
    }

    private void startTimeout() {
        if (timeoutTimer == null) {
            timeoutTimer = UserThread.runAfter(() -> {
                log.debug("Timeout reached at " + this);
                model.getOffer().setState(Offer.State.MAKER_OFFLINE);
                errorMessageHandler.handleErrorMessage("Timeout reached: Peer has not responded.");
            }, TIMEOUT);
        } else {
            log.warn("timeoutTimer already created. That must not happen.");
        }
    }

    private void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    private void handleTaskRunnerSuccess(String info, @Nullable OfferAvailabilityResponse message) {
        log.debug("handleTaskRunnerSuccess " + info);

        if (message != null)
            sendAckMessage(message, true, null);
    }

    private void handleTaskRunnerFault(String errorMessage, @Nullable OfferAvailabilityResponse message) {
        log.error(errorMessage);

        stopTimeout();
        errorMessageHandler.handleErrorMessage(errorMessage);

        if (message != null)
            sendAckMessage(message, false, errorMessage);
    }

    private void sendAckMessage(OfferAvailabilityResponse message, boolean result, @Nullable String errorMessage) {
        String offerId = message.getOfferId();
        String sourceUid = message.getUid();
        final NodeAddress makersNodeAddress = model.getPeerNodeAddress();
        PubKeyRing makersPubKeyRing = model.getOffer().getPubKeyRing();
        log.info("Send AckMessage for OfferAvailabilityResponse to peer {} with offerId {} and sourceUid {}",
                makersNodeAddress, offerId, sourceUid);

        AckMessage ackMessage = new AckMessage(model.getP2PService().getNetworkNode().getNodeAddress(),
                AckMessageSourceType.OFFER_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                offerId,
                result,
                errorMessage);
        model.getP2PService().sendEncryptedDirectMessage(
                makersNodeAddress,
                makersPubKeyRing,
                ackMessage,
                new SendDirectMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for OfferAvailabilityResponse arrived at makersNodeAddress {}. " +
                                        "offerId={}, sourceUid={}",
                                makersNodeAddress, offerId, ackMessage.getSourceUid());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for OfferAvailabilityResponse failed. AckMessage={}, " +
                                        "makersNodeAddress={}, errorMessage={}",
                                ackMessage, makersNodeAddress, errorMessage);
                    }
                }
        );
    }
}
