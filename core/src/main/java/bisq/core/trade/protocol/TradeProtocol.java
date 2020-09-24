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
import bisq.core.trade.messages.DepositTxAndDelayedPayoutTxMessage;
import bisq.core.trade.messages.TradeMessage;

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
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.taskrunner.Task;

import javafx.beans.value.ChangeListener;

import java.security.PublicKey;

import java.util.HashSet;
import java.util.Set;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static bisq.core.util.Validator.isTradeIdValid;
import static bisq.core.util.Validator.nonEmptyStringOf;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class TradeProtocol {
    interface Event {
        String name();
    }

    enum DisputeEvent implements TradeProtocol.Event {
        MEDIATION_RESULT_ACCEPTED,
        MEDIATION_RESULT_REJECTED
    }

    private static final long DEFAULT_TIMEOUT_SEC = 180;

    protected final ProcessModel processModel;
    private final DecryptedDirectMessageListener decryptedDirectMessageListener;
    private final ChangeListener<Trade.State> stateChangeListener;
    protected Trade trade;
    private Timer timeoutTimer;

    public TradeProtocol(Trade trade) {
        this.trade = trade;
        this.processModel = trade.getProcessModel();

        decryptedDirectMessageListener = (decryptedMessageWithPubKey, peer) -> {
            // We check the sig only as soon we have stored the peers pubKeyRing.
            PubKeyRing tradingPeerPubKeyRing = processModel.getTradingPeer().getPubKeyRing();
            PublicKey signaturePubKey = decryptedMessageWithPubKey.getSignaturePubKey();
            if (tradingPeerPubKeyRing != null && signaturePubKey.equals(tradingPeerPubKeyRing.getSignaturePubKey())) {
                NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
                if (networkEnvelope instanceof TradeMessage) {
                    TradeMessage message = (TradeMessage) networkEnvelope;
                    nonEmptyStringOf(message.getTradeId());

                    if (message.getTradeId().equals(processModel.getOfferId())) {
                        doHandleDecryptedMessage(message, peer);
                    }
                } else if (networkEnvelope instanceof AckMessage) {
                    AckMessage ackMessage = (AckMessage) networkEnvelope;
                    if (ackMessage.getSourceType() == AckMessageSourceType.TRADE_MESSAGE &&
                            ackMessage.getSourceId().equals(trade.getId())) {
                        // We handle the ack for CounterCurrencyTransferStartedMessage and DepositTxAndDelayedPayoutTxMessage
                        // as we support automatic re-send of the msg in case it was not ACKed after a certain time
                        if (ackMessage.getSourceMsgClassName().equals(CounterCurrencyTransferStartedMessage.class.getSimpleName())) {
                            processModel.setPaymentStartedAckMessage(ackMessage);
                        } else if (ackMessage.getSourceMsgClassName().equals(DepositTxAndDelayedPayoutTxMessage.class.getSimpleName())) {
                            processModel.setDepositTxSentAckMessage(ackMessage);
                        }

                        if (ackMessage.isSuccess()) {
                            log.info("Received AckMessage for {} from {} with tradeId {} and uid {}",
                                    ackMessage.getSourceMsgClassName(), peer, ackMessage.getSourceId(), ackMessage.getSourceUid());
                        } else {
                            log.warn("Received AckMessage with error state for {} from {} with tradeId {} and errorMessage={}",
                                    ackMessage.getSourceMsgClassName(), peer, ackMessage.getSourceId(), ackMessage.getErrorMessage());
                        }
                    }
                }
            }
        };
        processModel.getP2PService().addDecryptedDirectMessageListener(decryptedDirectMessageListener);

        //todo move
        stateChangeListener = (observable, oldValue, newValue) -> {
            if (newValue.getPhase() == Trade.Phase.TAKER_FEE_PUBLISHED && trade instanceof MakerTrade)
                processModel.getOpenOfferManager().closeOpenOffer(checkNotNull(trade.getOffer()));
        };
        trade.stateProperty().addListener(stateChangeListener);

    }

    protected abstract void doHandleDecryptedMessage(TradeMessage message, NodeAddress peer);


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
                TradeMessage message = (TradeMessage) networkEnvelope;
                NodeAddress peerNodeAddress = ((MailboxMessage) networkEnvelope).getSenderNodeAddress();
                doApplyMailboxTradeMessage(message, peerNodeAddress);
            }
        } else {
            log.error("SignaturePubKey in message does not match the SignaturePubKey we have stored to that trading peer.");
        }
    }

    protected void doApplyMailboxTradeMessage(TradeMessage message, NodeAddress peerNodeAddress) {
        log.info("Received {} as MailboxMessage from {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), peerNodeAddress, message.getTradeId(), message.getUid());
    }

    protected void startTimeout() {
        startTimeout(DEFAULT_TIMEOUT_SEC);
    }

    protected void startTimeout(long timeoutSec) {
        stopTimeout();

        timeoutTimer = UserThread.runAfter(() -> {
            log.error("Timeout reached. TradeID={}, state={}, timeoutSec={}",
                    trade.getId(), trade.stateProperty().get(), timeoutSec);
            trade.setErrorMessage("Timeout reached. Protocol did not complete in " + timeoutSec + " sec.");
            cleanupTradeOnFault();
            cleanup();
        }, timeoutSec);
    }

    protected void stopTimeout() {
        if (timeoutTimer != null) {
            timeoutTimer.stop();
            timeoutTimer = null;
        }
    }

    protected void handleTaskRunnerSuccess(TradeMessage message) {
        handleTaskRunnerSuccess(message, null);
    }

    protected void handleTaskRunnerSuccess(Event event) {
        handleTaskRunnerSuccess(null, event.name());
    }

    private void handleTaskRunnerSuccess(@Nullable TradeMessage message, @Nullable String trigger) {
        String triggerEvent = trigger != null ? trigger :
                message != null ? message.getClass().getSimpleName() : "N/A";
        log.info("TaskRunner successfully completed. {}", "Triggered from message " + triggerEvent);

        sendAckMessage(message, true, null);
    }

    protected void handleTaskRunnerFault(@Nullable TradeMessage message, String errorMessage) {
        log.error("Task runner failed on {} with error {}", message, errorMessage);

        sendAckMessage(message, false, errorMessage);

        cleanupTradeOnFault();
        cleanup();
    }

    protected void handleTaskRunnerFault(@Nullable Event event, String errorMessage) {
        log.error("Task runner failed on {} with error {}", event, errorMessage);

        cleanupTradeOnFault();
        cleanup();
    }


    protected void sendAckMessage(@Nullable TradeMessage message, boolean result, @Nullable String errorMessage) {
        // We complete at initial protocol setup with the setup listener tasks.
        // Other cases are if we start from an UI event the task runner (payment started, confirmed).
        // In such cases we have not set any message and we ignore the sendAckMessage call.
        if (message == null)
            return;

        String tradeId = message.getTradeId();
        String sourceUid = message.getUid();

        AckMessage ackMessage = new AckMessage(processModel.getMyNodeAddress(),
                AckMessageSourceType.TRADE_MESSAGE,
                message.getClass().getSimpleName(),
                sourceUid,
                tradeId,
                result,
                errorMessage);
        // If there was an error during offer verification, the tradingPeerNodeAddress of the trade might not be set yet.
        // We can find the peer's node address in the processModel's tempTradingPeerNodeAddress in that case.
        NodeAddress peer = trade.getTradingPeerNodeAddress() != null ?
                trade.getTradingPeerNodeAddress() :
                processModel.getTempTradingPeerNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, sourceUid={}",
                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
        processModel.getP2PService().sendEncryptedMailboxMessage(
                peer,
                processModel.getTradingPeer().getPubKeyRing(),
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for {} arrived at peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage for {} stored in mailbox for peer {}. tradeId={}, sourceUid={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for {} failed. Peer {}. tradeId={}, sourceUid={}, errorMessage={}",
                                ackMessage.getSourceMsgClassName(), peer, tradeId, sourceUid, errorMessage);
                    }
                }
        );
    }

    private void cleanupTradeOnFault() {
        Trade.State state = trade.getState();
        log.warn("cleanupTradableOnFault tradeState={}", state);
        TradeManager tradeManager = processModel.getTradeManager();
        if (trade.isInPreparation()) {
            // no funds left. we just clean up the trade list
            tradeManager.removePreparedTrade(trade);
        } else if (!trade.isFundsLockedIn()) {
            if (processModel.getPreparedDepositTx() == null) {
                if (trade.isTakerFeePublished()) {
                    tradeManager.addTradeToFailedTrades(trade);
                } else {
                    tradeManager.addTradeToClosedTrades(trade);
                }
            } else {
                log.error("We have already sent the prepared deposit tx to the peer but we did not received the reply " +
                        "about the deposit tx nor saw it in the network. tradeId={}, tradeState={}", trade.getId(), trade.getState());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // FluentProtocol
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected FluentProtocol given(Condition condition) {
        return new FluentProtocol(condition);
    }

    protected Condition phase(Trade.Phase expectedPhase) {
        return new Condition(trade, expectedPhase);
    }

    protected Condition anyPhase(Trade.Phase... expectedPhases) {
        return new Condition(trade, expectedPhases);
    }

    @SafeVarargs
    public final Setup tasks(Class<? extends Task<Trade>>... tasks) {
        return new Setup(trade, tasks);
    }

    // Main class. Contains the condition and setup, if condition is valid it will execute the
    // taskRunner and the optional runnable.
    class FluentProtocol {
        private final Condition condition;
        private Setup setup;

        public FluentProtocol(Condition condition) {
            this.condition = condition;
        }

        protected FluentProtocol setup(Setup setup) {
            this.setup = setup;
            return this;
        }

        // Can be used before or after executeTasks
        public FluentProtocol run(Runnable runnable) {
            if (condition.isValid()) {
                runnable.run();
            }
            return this;
        }

        public FluentProtocol executeTasks() {
            if (condition.isValid()) {
                if (setup.getTimeoutSec() > 0) {
                    startTimeout(setup.getTimeoutSec());
                }

                NodeAddress peer = condition.getPeer();
                if (peer != null) {
                    processModel.setTempTradingPeerNodeAddress(peer);
                }

                TradeMessage message = condition.getMessage();
                if (message != null) {
                    processModel.setTradeMessage(message);
                }
                TradeTaskRunner taskRunner = setup.getTaskRunner(message, condition.getEvent());
                taskRunner.addTasks(setup.getTasks());
                taskRunner.run();
            }
            return this;
        }
    }

    //
    static class Condition {
        private final Trade trade;
        @Nullable
        @Getter
        private TradeMessage message;
        private final Set<Trade.Phase> expectedPhases = new HashSet<>();
        private final Set<Trade.State> expectedStates = new HashSet<>();
        private final Set<Boolean> preConditions = new HashSet<>();
        @Nullable
        @Getter
        private Event event;
        @Getter
        private NodeAddress peer;
        private boolean isValid;
        private boolean isValidated;
        private Runnable preConditionFailedHandler;

        public Condition(Trade trade, Trade.Phase expectedPhase) {
            this.expectedPhases.add(expectedPhase);
            this.trade = trade;
        }

        public Condition(Trade trade, Trade.Phase... expectedPhases) {
            this.expectedPhases.addAll(Set.of(expectedPhases));
            this.trade = trade;
        }

        public Condition state(Trade.State state) {
            this.expectedStates.add(state);
            return this;
        }

        public Condition anyState(Trade.State... states) {
            this.expectedStates.addAll(Set.of(states));
            return this;
        }

        public Condition with(Event event) {
            checkArgument(!isValidated);
            this.event = event;
            return this;
        }

        public Condition with(TradeMessage message) {
            checkArgument(!isValidated);
            this.message = message;
            return this;
        }

        public Condition from(NodeAddress peer) {
            checkArgument(!isValidated);
            this.peer = peer;
            return this;
        }

        public Condition preCondition(boolean preCondition) {
            checkArgument(!isValidated);
            preConditions.add(preCondition);
            return this;
        }

        public Condition preCondition(boolean preCondition, Runnable conditionFailedHandler) {
            checkArgument(!isValidated);
            preConditions.add(preCondition);
            this.preConditionFailedHandler = conditionFailedHandler;
            return this;
        }

        private boolean isValid() {
            if (!isValidated) {
                boolean isPhaseValid = isPhaseValid();
                boolean isStateValid = isStateValid();

                boolean allPreConditionsMet = preConditions.stream().allMatch(e -> e);
                boolean isTradeIdValid = message == null || isTradeIdValid(trade.getId(), message);

                if (!allPreConditionsMet) {
                    log.error("PreConditions not met. preConditions={}, this={}", preConditions, this);
                    if (preConditionFailedHandler != null) {
                        preConditionFailedHandler.run();
                    }
                }
                if (!isTradeIdValid) {
                    log.error("TradeId does not match tradeId in message, TradeId={}, tradeId in message={}",
                            trade.getId(), message.getTradeId());
                }

                isValid = isPhaseValid && isStateValid && allPreConditionsMet && isTradeIdValid;
                isValidated = true;
            }
            return isValid;
        }

        private boolean isPhaseValid() {
            if (expectedPhases.isEmpty()) {
                return true;
            }

            boolean isPhaseValid = expectedPhases.stream().anyMatch(e -> e == trade.getPhase());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isPhaseValid) {
                log.info("We received {} at phase {} and state {}",
                        trigger,
                        trade.getPhase(),
                        trade.getState());
            } else {
                log.error("We received {} but we are are not in the correct phase. Expected phases={}, " +
                                "Trade phase={}, Trade state= {} ",
                        trigger,
                        expectedPhases,
                        trade.getPhase(),
                        trade.getState());
            }

            return isPhaseValid;
        }

        private boolean isStateValid() {
            if (expectedStates.isEmpty()) {
                return true;
            }

            boolean isStateValid = expectedStates.stream().anyMatch(e -> e == trade.getState());
            String trigger = message != null ?
                    message.getClass().getSimpleName() :
                    event != null ?
                            event.name() + " event" :
                            "";
            if (isStateValid) {
                log.info("We received {} at state {}",
                        trigger,
                        trade.getState());
            } else {
                log.error("We received {} but we are are not in the correct state. Expected states={}, " +
                                "Trade state= {} ",
                        trigger,
                        expectedStates,
                        trade.getState());
            }

            return isStateValid;
        }
    }

    // Setup for task runner
    class Setup {
        private final Trade trade;
        @Getter
        private final Class<? extends Task<Trade>>[] tasks;
        @Getter
        private int timeoutSec;
        @Nullable
        private TradeTaskRunner taskRunner;

        @SafeVarargs
        public Setup(Trade trade, Class<? extends Task<Trade>>... tasks) {
            this.trade = trade;
            this.tasks = tasks;
        }

        public Setup withTimeout(int timeoutSec) {
            this.timeoutSec = timeoutSec;
            return this;
        }

        public Setup using(TradeTaskRunner taskRunner) {
            this.taskRunner = taskRunner;
            return this;
        }

        private TradeTaskRunner getTaskRunner(@Nullable TradeMessage message, @Nullable Event event) {
            if (taskRunner == null) {
                if (message != null) {
                    taskRunner = new TradeTaskRunner(trade,
                            () -> handleTaskRunnerSuccess(message),
                            errorMessage -> handleTaskRunnerFault(message, errorMessage));
                } else if (event != null) {
                    taskRunner = new TradeTaskRunner(trade,
                            () -> handleTaskRunnerSuccess(event),
                            errorMessage -> handleTaskRunnerFault(event, errorMessage));
                } else {
                    throw new IllegalStateException("addTasks must not be called without message or event " +
                            "set in case no taskRunner has been created yet");
                }
            }
            return taskRunner;
        }
    }
}
