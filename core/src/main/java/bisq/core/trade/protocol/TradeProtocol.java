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

package bisq.core.trade.protocol;

import bisq.core.trade.MakerTrade;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.messages.CounterCurrencyTransferStartedMessage;
import bisq.core.trade.messages.InputsForDepositTxRequest;
import bisq.core.trade.messages.MediatedPayoutTxPublishedMessage;
import bisq.core.trade.messages.MediatedPayoutTxSignatureMessage;
import bisq.core.trade.messages.PeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.tasks.ApplyFilter;
import bisq.core.trade.protocol.tasks.ProcessPeerPublishedDelayedPayoutTxMessage;
import bisq.core.trade.protocol.tasks.mediation.BroadcastMediatedPayoutTx;
import bisq.core.trade.protocol.tasks.mediation.FinalizeMediatedPayoutTx;
import bisq.core.trade.protocol.tasks.mediation.ProcessMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.tasks.mediation.ProcessMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.mediation.SendMediatedPayoutSignatureMessage;
import bisq.core.trade.protocol.tasks.mediation.SendMediatedPayoutTxPublishedMessage;
import bisq.core.trade.protocol.tasks.mediation.SetupMediatedPayoutTxListener;
import bisq.core.trade.protocol.tasks.mediation.SignMediatedPayoutTx;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.DecryptedDirectMessageListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.ErrorMessageHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.network.NetworkEnvelope;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class TradeProtocol {
    private static final long TIMEOUT = 90;

    protected final ProcessModel processModel;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;
    private final ChangeListener<Trade.State> stateChangeListener;
    protected Trade trade;
    private Timer timeoutTimer;

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peersNodeAddress) -> {
            // We check the sig only as soon we have stored the peers pubKeyRing.
            PubKeyRing tradingPeerPubKeyRing = processModel.getTradingPeer().getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.getSignaturePubKey();
            if (tradingPeerPubKeyRing != null && signaturePubKey.equals(tradingPeerPubKeyRing.getSignaturePubKey())) {
                NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
                if (networkEnvelope instanceof TradeMessage) {
                    TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                    nonEmptyStringOf(tradeMessage.getTradeId());

                    if (tradeMessage.getTradeId().equals(processModel.getOfferId())) {
                        doHandleDecryptedMessage(tradeMessage, peersNodeAddress);
                    }
                } else if (networkEnvelope instanceof AckMessage) {
                    AckMessage ackMessage = (AckMessage) networkEnvelope;
                    if (ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                            ackMessage.getSourceId().equals(trade.getId())) {
                        // We only handle the ack for CounterCurrencyTransferStartedMessage
                        if (ackMessage.getSourceMsgClassName().equals(CounterCurrencyTransferStartedMessage.class.getSimpleName()))
                            processModel.setPaymentStartedAckMessage(ackMessage);

                        if (ackMessage.isSuccess()) {
                            log.info("Received AckMessage for {} from {} with tradeId {} and uid {}",
                                    ackMessage.getSourceMsgClassName(), peersNodeAddress, ackMessage.getSourceId(), ackMessage.getSourceUid());
                        } else {
                            log.warn("Received AckMessage with error state for {} from {} with tradeId {} and errorMessage={}",
                                    ackMessage.getSourceMsgClassName(), peersNodeAddress, ackMessage.getSourceId(), ackMessage.getErrorMessage());
                        }
                    }
                }
            }
        };
        processModel.getP2PService().addDecryptedDirectMessageListener(decryptedDirectMessageListener);

        stateChangeListener = (observable, oldValue, newValue) -> {
            if (newValue.getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED && trade instanceof MakerTrade)
                processModel.getOpenOfferManager().closeOpenOffer(checkNotNull(trade.getOffer()));
        };
        trade.stateProperty().addListener(stateChangeListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mediation: Called from UI if trader accepts mediation result
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Trader has not yet received the peer's signature but has clicked the accept button.
    public void onAcceptMediationResult(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getProcessModel().getTradingPeer().getMediatedPayoutTxSignature() != null) {
            errorMessageHandler.handleErrorMessage("We have received already the signature from the peer.");
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess("onAcceptMediationResult");
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SignMediatedPayoutTx.class,
                SendMediatedPayoutSignatureMessage.class,
                SetupMediatedPayoutTxListener.class
        );
        taskRunner.run();
    }


    // Trader has already received the peer's signature and has clicked the accept button as well.
    public void onFinalizeMediationResultPayout(ResultHandler resultHandler, ErrorMessageHandler errorMessageHandler) {
        if (trade.getPayoutTx() != null) {
            errorMessageHandler.handleErrorMessage("Payout tx is already published.");
            return;
        }

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> {
                    resultHandler.handleResult();
                    handleTaskRunnerSuccess("onAcceptMediationResult");
                },
                (errorMessage) -> {
                    errorMessageHandler.handleErrorMessage(errorMessage);
                    handleTaskRunnerFault(errorMessage);
                });
        taskRunner.addTasks(
                ApplyFilter.class,
                SignMediatedPayoutTx.class,
                FinalizeMediatedPayoutTx.class,
                BroadcastMediatedPayoutTx.class,
                SendMediatedPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Mediation: incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handle(MediatedPayoutTxSignatureMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "MediatedPayoutSignatureMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                ProcessMediatedPayoutSignatureMessage.class
        );
        taskRunner.run();
    }

    protected void handle(MediatedPayoutTxPublishedMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "handle PayoutTxPublishedMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                ProcessMediatedPayoutTxPublishedMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Peer has published the delayed payout tx
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void handle(PeerPublishedDelayedPayoutTxMessage tradeMessage, NodeAddress sender) {
        processModel.setTradeMessage(tradeMessage);
        processModel.setTempTradingPeerNodeAddress(sender);

        TradeTaskRunner taskRunner = new TradeTaskRunner(trade,
                () -> handleTaskRunnerSuccess(tradeMessage, "PeerPublishedDelayedPayoutTxMessage"),
                errorMessage -> handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                //todo
                ProcessPeerPublishedDelayedPayoutTxMessage.class
        );
        taskRunner.run();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Dispatcher
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender) {
        if (tradeMessage instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) tradeMessage, sender);
        } else if (tradeMessage instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) tradeMessage, sender);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void completed() {
        cleanup();
    }

    private void cleanup() {
        stopTimeout();
        trade.stateProperty().removeListener(stateChangeListener);
        // We removed that from here earlier as it broke the trade process in some non critical error cases.
        // But it should be actually removed...
        processModel.getP2PService().removeDecryptedDirectMessageListener(decryptedDirectMessageListener);
    }

    public void applyMailboxMessage(DecryptedMessageWithPubKey decryptedMessageWithPubKey, Trade trade) {
        NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
        if (processModel.getTradingPeer().getPubKeyRing() != null &&
                decryptedMessageWithPubKey.getSignaturePubKey().equals(processModel.getTradingPeer().getPubKeyRing().getSignaturePubKey())) {
            processModel.setDecryptedMessageWithPubKey(decryptedMessageWithPubKey);

            if (networkEnvelope instanceof MailboxMessage && networkEnvelope instanceof TradeMessage) {
                this.trade = trade;
                TradeMessage tradeMessage = (TradeMessage) networkEnvelope;
                NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelope).getSenderNodeAddress();
                doApplyMailboxTradeMessage(tradeMessage, peerNodeAddress);
            }
        } else {
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
        }
    }

    protected void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress) {
        log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                tradeMessage.getClass().getSimpleName(), peerNodeAddress, tradeMessage.getTradeId(), tradeMessage.getUid());

        if (tradeMessage instanceof MediatedPayoutTxSignatureMessage) {
            handle((MediatedPayoutTxSignatureMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof MediatedPayoutTxPublishedMessage) {
            handle((MediatedPayoutTxPublishedMessage) tradeMessage, peerNodeAddress);
        } else if (tradeMessage instanceof PeerPublishedDelayedPayoutTxMessage) {
            handle((PeerPublishedDelayedPayoutTxMessage) tradeMessage, peerNodeAddress);
        }
    }

    protected void startTimeout() {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID={}, state={}", trade.getId(), trade.stateProperty().get());
            trade.setErrorMessage("A timeout occurred.");
            cleanupTradableOnFault();
            cleanup();
        }, TIMEOUT);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerSuccess(String info) {
        handleTaskRunnerSuccess(null, info);
    }

    protected void handleTaskRunnerSuccess(@Nullable TradeMessage tradeMessage, String info) {
        log.debug("handleTaskRunnerSuccess {}", info);

        sendAckMessage(tradeMessage, true, null);
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        handleTaskRunnerFault(null, errorMessage);
    }

    protected void handleTaskRunnerFault(@Nullable TradeMessage tradeMessage, String errorMessage) {
        log.error(errorMessage);

        sendAckMessage(tradeMessage, false, errorMessage);

        cleanupTradableOnFault();
        cleanup();
    }

    private void sendAckMessage(@Nullable TradeMessage tradeMessage, boolean result, @Nullable String errorMessage) {
        // We complete at initial protocol setup with the setup listener tasks.
        // Other cases are if we start from an UI event the task runner (payment started, confirmed).
        // In such cases we have not set any tradeMessage and we ignore the sendAckMessage call.
        if (tradeMessage == null)
            return;

        String tradeId = tradeMessage.getTradeId();
        String sourceUid = "";
        if (tradeMessage instanceof MailboxMessage) {
            sourceUid = ((MailboxMessage) tradeMessage).getUid();
        } else {
            // For direct msg we don't have a mandatory uid so we need to cast to get it
            if (tradeMessage instanceof InputsForDepositTxRequest) {
                sourceUid = tradeMessage.getUid();
            }
        }
        AckMessage ackMessage = new AckMessage(processModel.getMyNodeAddress(),
                AckMessageSourceType.TRADE_MESSAGE,
                tradeMessage.getClass().getSimpleName(),
                sourceUid,
                tradeId,
                result,
                errorMessage);
        // If there was an error during offer verification, the tradingPeerNodeAddress of the trade might not be set yet.
        // We can find the peer's node address in the processModel's tempTradingPeerNodeAddress in that case.
        final NodeAddress peersNodeAddress = trade.getTradingPeerNodeAddress() != null ? trade.getTradingPeerNodeAddress() : processModel.getTempTradingPeerNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, sourceUid={}",
                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, sourceUid);
        String finalSourceUid = sourceUid;
        processModel.getP2PService().sendEncryptedMailboxMessage(
                peersNodeAddress,
                processModel.getTradingPeer().getPubKeyRing(),
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for {} arrived at peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, finalSourceUid);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage for {} stored in mailbox for peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, finalSourceUid);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for {} failed. Peer {}. tradeId={}, sourceUid={}, errorMessage={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, finalSourceUid, errorMessage);
                    }
                }
        );
    }

    private void cleanupTradableOnFault() {
        final Trade.State state = trade.getState();
        log.warn("cleanupTradableOnFault tradeState={}", state);
        TradeManager tradeManager = processModel.getTradeManager();
        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else if (!trade.isFundsLockedIn()) {
            if (processModel.getPreparedDepositTx() == null) {
                if (trade.isTakerFeePublished())
                    tradeManager.addTradeToFailedTrades(trade);
                else
                    tradeManager.addTradeToClosedTrades(trade);
            } else {
                log.error("We have already sent the prepared deposit tx to the peer but we did not received the reply " +
                        "about the deposit tx nor saw it in the network. tradeId={}, tradeState={}", trade.getId(), trade.getState());
            }
        }
    }
}
