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

package bisq.core.support.dispute;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.Restrictions;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.dao.DaoFacade;
import bisq.core.locale.CurrencyUtil;
import bisq.core.locale.Res;
import bisq.core.monetary.Altcoin;
import bisq.core.monetary.Price;
import bisq.core.offer.OpenOfferManager;
import bisq.core.offer.bisq_v1.OfferPayload;
import bisq.core.provider.price.MarketPrice;
import bisq.core.provider.price.PriceFeedService;
import bisq.core.support.SupportManager;
import bisq.core.support.dispute.mediation.MediationResultState;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.ClosedTradableManager;
import bisq.core.trade.TradeManager;
import bisq.core.trade.bisq_v1.FailedTradesManager;
import bisq.core.trade.bisq_v1.TradeDataValidation;
import bisq.core.trade.model.bisq_v1.Contract;
import bisq.core.trade.model.bisq_v1.Trade;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.config.Config;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.util.MathUtils;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.Coin;
import org.bitcoinj.utils.Fiat;

import javafx.beans.property.IntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.security.KeyPair;

import java.time.Instant;

import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class DisputeManager<T extends DisputeList<Dispute>> extends SupportManager {
    protected final TradeWalletService tradeWalletService;
    protected final BtcWalletService btcWalletService;
    protected final TradeManager tradeManager;
    protected final ClosedTradableManager closedTradableManager;
    private final FailedTradesManager failedTradesManager;
    protected final OpenOfferManager openOfferManager;
    protected final PubKeyRing pubKeyRing;
    protected final DisputeListService<T> disputeListService;
    private final Config config;
    private final PriceFeedService priceFeedService;
    protected final DaoFacade daoFacade;
    protected String pendingOutgoingMessage;

    @Getter
    protected final ObservableList<DisputeValidation.ValidationException> validationExceptions =
            FXCollections.observableArrayList();
    @Getter
    private final KeyPair signatureKeyPair;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeManager(P2PService p2PService,
                          TradeWalletService tradeWalletService,
                          BtcWalletService btcWalletService,
                          WalletsSetup walletsSetup,
                          TradeManager tradeManager,
                          ClosedTradableManager closedTradableManager,
                          FailedTradesManager failedTradesManager,
                          OpenOfferManager openOfferManager,
                          DaoFacade daoFacade,
                          KeyRing keyRing,
                          DisputeListService<T> disputeListService,
                          Config config,
                          PriceFeedService priceFeedService) {
        super(p2PService, walletsSetup);

        this.tradeWalletService = tradeWalletService;
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.failedTradesManager = failedTradesManager;
        this.openOfferManager = openOfferManager;
        this.daoFacade = daoFacade;
        this.pubKeyRing = keyRing.getPubKeyRing();
        signatureKeyPair = keyRing.getSignatureKeyPair();
        this.disputeListService = disputeListService;
        this.config = config;
        this.priceFeedService = priceFeedService;
        clearPendingMessage();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void requestPersistence() {
        disputeListService.requestPersistence();
    }

    @Override
    public NodeAddress getPeerNodeAddress(ChatMessage message) {
        Optional<Dispute> disputeOptional = findDispute(message);
        if (disputeOptional.isEmpty()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }
        return getNodeAddressPubKeyRingTuple(disputeOptional.get()).first;
    }

    @Override
    public PubKeyRing getPeerPubKeyRing(ChatMessage message) {
        Optional<Dispute> disputeOptional = findDispute(message);
        if (disputeOptional.isEmpty()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }

        return getNodeAddressPubKeyRingTuple(disputeOptional.get()).second;
    }

    @Override
    public List<ChatMessage> getAllChatMessages(String tradeId) {
        return getDisputeList().stream()
                .filter(dispute -> dispute.getTradeId().equals(tradeId))
                .flatMap(dispute -> dispute.getChatMessages().stream())
                .collect(Collectors.toList());
    }

    @Override
    public boolean channelOpen(ChatMessage message) {
        return findDispute(message).isPresent();
    }

    @Override
    public void addAndPersistChatMessage(ChatMessage message) {
        findDispute(message).ifPresent(dispute -> {
            if (dispute.getChatMessages().stream().noneMatch(m -> m.getUid().equals(message.getUid()))) {
                dispute.addAndPersistChatMessage(message);
                requestPersistence();
            } else {
                log.warn("We got a chatMessage that we have already stored. UId = {} TradeId = {}",
                        message.getUid(), message.getTradeId());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Abstract methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We get that message at both peers. The dispute object is in context of the trader
    public abstract void onDisputeResultMessage(DisputeResultMessage disputeResultMessage);

    @Nullable
    public abstract NodeAddress getAgentNodeAddress(Dispute dispute);

    protected abstract Trade.DisputeState getDisputeStateStartedByPeer();

    public abstract void cleanupDisputes();

    protected abstract String getDisputeInfo(Dispute dispute);

    protected abstract String getDisputeIntroForPeer(String disputeInfo);

    protected abstract String getDisputeIntroForDisputeCreator(String disputeInfo);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates for disputeListService
    ///////////////////////////////////////////////////////////////////////////////////////////

    public IntegerProperty getNumOpenDisputes() {
        return disputeListService.getNumOpenDisputes();
    }

    public ObservableList<Dispute> getDisputesAsObservableList() {
        return disputeListService.getObservableList();
    }

    public String getNrOfDisputes(boolean isBuyer, Contract contract) {
        return disputeListService.getNrOfDisputes(isBuyer, contract);
    }

    protected T getDisputeList() {
        return disputeListService.getDisputeList();
    }

    public Set<String> getDisputedTradeIds() {
        return disputeListService.getDisputedTradeIds();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        super.onAllServicesInitialized();
        disputeListService.onAllServicesInitialized();

        p2PService.addP2PServiceListener(new BootstrapListener() {
            @Override
            public void onDataReceived() {
                tryApplyMessages();
                checkDisputesForUpdates();
            }
        });

        walletsSetup.downloadPercentageProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.isDownloadComplete())
                tryApplyMessages();
        });

        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.hasSufficientPeersForBroadcast()) {
                tryApplyMessages();
            }
        });

        tryApplyMessages();
        cleanupDisputes();

        List<Dispute> disputes = getDisputeList().getList();
        disputes.forEach(dispute -> {
            try {
                DisputeValidation.validateNodeAddresses(dispute, config);
                if (dispute.isUsingLegacyBurningMan()) {
                    DisputeValidation.validateDonationAddressMatchesAnyPastParamValues(dispute, dispute.getDonationAddressOfDelayedPayoutTx(), daoFacade);
                }
            } catch (DisputeValidation.AddressException | DisputeValidation.NodeAddressException e) {
                log.error(e.toString());
                validationExceptions.add(e);
            }
        });

        DisputeValidation.testIfAnyDisputeTriedReplay(disputes,
                disputeReplayException -> {
                    log.error(disputeReplayException.toString());
                    validationExceptions.add(disputeReplayException);
                });

        maybeClearSensitiveData();
    }

    private void checkDisputesForUpdates() {
        List<Dispute> disputes = getDisputeList().getList();
        disputes.forEach(dispute -> {
            if (dispute.isResultProposed()) {
                // an open dispute where the mediator has proposed a result.  has the trade moved on?
                // if so, dispute can close and the mediator needs to be informed so they can close their ticket.
                tradeManager.getTradeById(dispute.getTradeId()).ifPresentOrElse(
                        t -> checkForMediatedTradePayout(t, dispute),
                        () -> closedTradableManager.getTradableById(dispute.getTradeId()).ifPresent(
                                t -> checkForMediatedTradePayout((Trade) t, dispute)));
            }
        });
    }

    protected void checkForMediatedTradePayout(Trade trade, Dispute dispute) {
        if (trade.disputeStateProperty().get().isArbitrated() || trade.getTradePhase() == Trade.Phase.PAYOUT_PUBLISHED) {
            disputedTradeUpdate(trade.getDisputeState().toString(), dispute, true);
        } else {
            // user accepted/rejected mediation proposal (before lockup period has expired)
            trade.mediationResultStateProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == MediationResultState.MEDIATION_RESULT_ACCEPTED ||
                        newValue == MediationResultState.MEDIATION_RESULT_REJECTED) {
                    disputedTradeUpdate(newValue.toString(), dispute, false);
                }
            });
            // user rejected mediation after lockup period: opening arbitration after peer redirects
            trade.disputeStateProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue.isEscalated()) {
                    disputedTradeUpdate(newValue.toString(), dispute, true);
                }
            });
            // trade paid out through mediation
            trade.statePhaseProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue == Trade.Phase.PAYOUT_PUBLISHED) {
                    disputedTradeUpdate(newValue.toString(), dispute, true);
                }
            });
        }
    }

    public boolean isTrader(Dispute dispute) {
        return pubKeyRing.equals(dispute.getTraderPubKeyRing());
    }


    public Optional<Dispute> findOwnDispute(String tradeId) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return Optional.empty();
        }
        return disputeList.stream().filter(e -> e.getTradeId().equals(tradeId)).findAny();
    }

    public void maybeClearSensitiveData() {
        log.info("{} checking closed disputes eligibility for having sensitive data cleared", super.getClass().getSimpleName());
        Instant safeDate = closedTradableManager.getSafeDateForSensitiveDataClearing();
        getDisputeList().getList().stream()
                .filter(e -> e.isClosed())
                .filter(e -> e.getOpeningDate().toInstant().isBefore(safeDate))
                .forEach(Dispute::maybeClearSensitiveData);
        requestPersistence();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean agentCheckDisputeHealth(Dispute disputeToCheck) {
        // checking from the agent perspective only
        if (disputeToCheck.getChatMessages().stream().anyMatch(ChatMessage::isSenderIsTrader)) {
            return true;
        }
        // consider only messages which have been transmitted
        List<ChatMessage> transmittedMessages = disputeToCheck.getChatMessages().stream()
                .filter(e -> !e.isSystemMessage())
                .filter(e -> !e.getStoredInMailboxProperty().get())
                .collect(Collectors.toList());
        if (transmittedMessages.size() == 0) {
            return true;
        }
        // dispute is healthy if any transmitted message has been ACKd by the peer
        return transmittedMessages.stream()
                .anyMatch(e -> e.acknowledgedProperty().get());
    }

    // dispute agent receives that from trader who opens dispute
    protected void onOpenNewDisputeMessage(OpenNewDisputeMessage openNewDisputeMessage) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        String errorMessage = null;
        Dispute dispute = openNewDisputeMessage.getDispute();
        // Disputes from clients < 1.2.0 always have support type ARBITRATION in dispute as the field didn't exist before
        dispute.setSupportType(openNewDisputeMessage.getSupportType());
        // disputes from clients < 1.6.0 have state not set as the field didn't exist before
        dispute.setState(Dispute.State.NEW);    // this can be removed a few months after 1.6.0 release

        Contract contract = dispute.getContract();
        addPriceInfoMessage(dispute, 0);

        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();
        if (isAgent(dispute)) {
            if (!disputeList.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (storedDisputeOptional.isEmpty()) {
                    disputeList.add(dispute);
                    sendPeerOpenedDisputeMessage(dispute, contract, peersPubKeyRing);
                } else {
                    // valid case if both have opened a dispute and agent was not online.
                    log.debug("We got a dispute already open for that trade and trading peer. TradeId = {}",
                            dispute.getTradeId());
                }
            } else {
                errorMessage = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
                log.warn(errorMessage);
            }
        } else {
            errorMessage = "Trader received openNewDisputeMessage. That must never happen.";
            log.error(errorMessage);
        }

        // We use the ChatMessage not the openNewDisputeMessage for the ACK
        ObservableList<ChatMessage> messages = dispute.getChatMessages();
        if (!messages.isEmpty()) {
            ChatMessage chatMessage = messages.get(0);
            PubKeyRing sendersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerPubKeyRing() : contract.getSellerPubKeyRing();
            sendAckMessage(chatMessage, sendersPubKeyRing, errorMessage == null, errorMessage);
        }

        addMediationResultMessage(dispute);

        try {
            DisputeValidation.validateDisputeData(dispute, btcWalletService);
            DisputeValidation.validateNodeAddresses(dispute, config);
            DisputeValidation.validateSenderNodeAddress(dispute, openNewDisputeMessage.getSenderNodeAddress());
            DisputeValidation.testIfDisputeTriesReplay(dispute, disputeList.getList());
            if (dispute.isUsingLegacyBurningMan()) {
                DisputeValidation.validateDonationAddressMatchesAnyPastParamValues(dispute, dispute.getDonationAddressOfDelayedPayoutTx(), daoFacade);
            }
        } catch (DisputeValidation.ValidationException e) {
            log.error(e.toString());
            validationExceptions.add(e);
        }
        requestPersistence();
    }

    // Not-dispute-requester receives that msg from dispute agent
    protected void onPeerOpenedDisputeMessage(PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        Dispute dispute = peerOpenedDisputeMessage.getDispute();
        tradeManager.getTradeById(dispute.getTradeId()).ifPresentOrElse(
                trade -> peerOpenedDisputeForTrade(peerOpenedDisputeMessage, dispute, trade),
                () -> closedTradableManager.getTradableById(dispute.getTradeId()).ifPresentOrElse(
                        closedTradable -> newDisputeRevertsClosedTrade(peerOpenedDisputeMessage, dispute, (Trade) closedTradable),
                        () -> failedTradesManager.getTradeById(dispute.getTradeId()).ifPresent(
                                trade -> newDisputeRevertsFailedTrade(peerOpenedDisputeMessage, dispute, trade))));
    }

    private void newDisputeRevertsFailedTrade(PeerOpenedDisputeMessage peerOpenedDisputeMessage,
                                              Dispute dispute,
                                              Trade trade) {
        log.info("Peer dispute ticket received, reverting failed trade {} to pending", trade.getShortId());
        failedTradesManager.removeTrade(trade);
        tradeManager.addTradeToPendingTrades(trade);
        peerOpenedDisputeForTrade(peerOpenedDisputeMessage, dispute, trade);
    }

    private void newDisputeRevertsClosedTrade(PeerOpenedDisputeMessage peerOpenedDisputeMessage,
                                              Dispute dispute,
                                              Trade trade) {
        log.info("Peer dispute ticket received, reverting closed trade {} to pending", trade.getShortId());
        closedTradableManager.remove(trade);
        tradeManager.addTradeToPendingTrades(trade);
        peerOpenedDisputeForTrade(peerOpenedDisputeMessage, dispute, trade);
    }

    private void peerOpenedDisputeForTrade(PeerOpenedDisputeMessage peerOpenedDisputeMessage,
                                           Dispute dispute,
                                           Trade trade) {
        String errorMessage = null;
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        try {
            DisputeValidation.validateDisputeData(dispute, btcWalletService);
            DisputeValidation.validateNodeAddresses(dispute, config);
            DisputeValidation.validateTradeAndDispute(dispute, trade);
            TradeDataValidation.validateDelayedPayoutTx(trade,
                    trade.getDelayedPayoutTx(),
                    btcWalletService);
            if (dispute.isUsingLegacyBurningMan()) {
                DisputeValidation.validateDonationAddress(dispute,
                        Objects.requireNonNull(trade.getDelayedPayoutTx()),
                        btcWalletService.getParams());
                DisputeValidation.validateDonationAddressMatchesAnyPastParamValues(dispute, dispute.getDonationAddressOfDelayedPayoutTx(), daoFacade);
            }
        } catch (TradeDataValidation.ValidationException | DisputeValidation.ValidationException e) {
            // The peer sent us an invalid donation address. We do not return here as we don't want to break
            // mediation/arbitration and log only the issue. The dispute agent will run validation as well and will get
            // a popup displayed to react.
            log.warn("Donation address is invalid. {}", e.toString());
        }

        if (!isAgent(dispute)) {
            if (!disputeList.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (storedDisputeOptional.isEmpty()) {
                    disputeList.add(dispute);
                    trade.setDisputeState(getDisputeStateStartedByPeer());
                    tradeManager.requestPersistence();
                } else {
                    // valid case if both have opened a dispute and agent was not online.
                    log.debug("We got a dispute already open for that trade and trading peer. TradeId = {}",
                            dispute.getTradeId());
                }
            } else {
                errorMessage = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
                log.warn(errorMessage);
            }
        } else {
            errorMessage = "Arbitrator received peerOpenedDisputeMessage. That must never happen.";
            log.error(errorMessage);
        }

        // We use the ChatMessage not the peerOpenedDisputeMessage for the ACK
        ObservableList<ChatMessage> messages = peerOpenedDisputeMessage.getDispute().getChatMessages();
        if (!messages.isEmpty()) {
            ChatMessage msg = messages.get(0);
            sendAckMessage(msg, dispute.getAgentPubKeyRing(), errorMessage == null, errorMessage);
        }

        sendAckMessage(peerOpenedDisputeMessage, dispute.getAgentPubKeyRing(), errorMessage == null, errorMessage);
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Send message
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendOpenNewDisputeMessage(Dispute dispute,
                                          boolean reOpen,
                                          ResultHandler resultHandler,
                                          FaultHandler faultHandler) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        if (disputeList.contains(dispute)) {
            String msg = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
            log.warn(msg);
            faultHandler.handleFault(msg, new DisputeAlreadyOpenException());
            return;
        }

        Optional<Dispute> storedDisputeOptional = findDispute(dispute);
        if (storedDisputeOptional.isEmpty() || reOpen) {
            String disputeInfo = getDisputeInfo(dispute);
            String disputeMessage = getDisputeIntroForDisputeCreator(disputeInfo);
            String sysMsg = dispute.isSupportTicket() ?
                    Res.get("support.youOpenedTicket", disputeInfo, Version.VERSION)
                    : disputeMessage;

            String message = Res.get("support.systemMsg", sysMsg);
            ChatMessage chatMessage = new ChatMessage(
                    getSupportType(),
                    dispute.getTradeId(),
                    pubKeyRing.hashCode(),
                    false,
                    message,
                    p2PService.getAddress());
            chatMessage.setSystemMessage(true);
            dispute.addAndPersistChatMessage(chatMessage);
            if (!reOpen) {
                disputeList.add(dispute);
            }

            NodeAddress agentNodeAddress = getAgentNodeAddress(dispute);
            if (agentNodeAddress == null) {
                return;
            }

            OpenNewDisputeMessage openNewDisputeMessage = new OpenNewDisputeMessage(dispute,
                    p2PService.getAddress(),
                    UUID.randomUUID().toString(),
                    getSupportType());

            log.info("Send {} to peer {}. tradeId={}, openNewDisputeMessage.uid={}, chatMessage.uid={}",
                    openNewDisputeMessage.getClass().getSimpleName(),
                    agentNodeAddress,
                    openNewDisputeMessage.getTradeId(),
                    openNewDisputeMessage.getUid(),
                    chatMessage.getUid());

            recordPendingMessage(openNewDisputeMessage.getClass().getSimpleName());
            mailboxMessageService.sendEncryptedMailboxMessage(agentNodeAddress,
                    dispute.getAgentPubKeyRing(),
                    openNewDisputeMessage,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), agentNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    chatMessage.getUid());

                            clearPendingMessage();
                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setArrived(true);
                            requestPersistence();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), agentNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    chatMessage.getUid());

                            clearPendingMessage();
                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setStoredInMailbox(true);
                            requestPersistence();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}, errorMessage={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), agentNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    chatMessage.getUid(), errorMessage);

                            clearPendingMessage();
                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setSendMessageError(errorMessage);
                            requestPersistence();
                            faultHandler.handleFault("Sending dispute message failed: " +
                                    errorMessage, new DisputeMessageDeliveryFailedException());
                        }
                    }
            );
        } else {
            String msg = "We got a dispute already open for that trade and trading peer.\n" +
                    "TradeId = " + dispute.getTradeId();
            log.warn(msg);
            faultHandler.handleFault(msg, new DisputeAlreadyOpenException());
        }
        requestPersistence();
    }

    // Dispute agent sends that to trading peer when he received openDispute request
    private void sendPeerOpenedDisputeMessage(Dispute disputeFromOpener,
                                              Contract contractFromOpener,
                                              PubKeyRing pubKeyRing) {
        // We delay a bit for sending the message to the peer to allow that a openDispute message from the peer is
        // being used as the valid msg. If dispute agent was offline and both peer requested we want to see the correct
        // message and not skip the system message of the peer as it would be the case if we have created the system msg
        // from the code below.
        UserThread.runAfter(() -> doSendPeerOpenedDisputeMessage(disputeFromOpener,
                        contractFromOpener,
                        pubKeyRing),
                100, TimeUnit.MILLISECONDS);
    }

    private void doSendPeerOpenedDisputeMessage(Dispute disputeFromOpener,
                                                Contract contractFromOpener,
                                                PubKeyRing pubKeyRing) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        Dispute dispute = new Dispute(new Date().getTime(),
                disputeFromOpener.getTradeId(),
                pubKeyRing.hashCode(),
                !disputeFromOpener.isDisputeOpenerIsBuyer(),
                !disputeFromOpener.isDisputeOpenerIsMaker(),
                pubKeyRing,
                disputeFromOpener.getTradeDate().getTime(),
                disputeFromOpener.getTradePeriodEnd().getTime(),
                contractFromOpener,
                disputeFromOpener.getContractHash(),
                disputeFromOpener.getDepositTxSerialized(),
                disputeFromOpener.getPayoutTxSerialized(),
                disputeFromOpener.getDepositTxId(),
                disputeFromOpener.getPayoutTxId(),
                disputeFromOpener.getContractAsJson(),
                disputeFromOpener.getMakerContractSignature(),
                disputeFromOpener.getTakerContractSignature(),
                disputeFromOpener.getAgentPubKeyRing(),
                disputeFromOpener.isSupportTicket(),
                disputeFromOpener.getSupportType());
        dispute.setExtraDataMap(disputeFromOpener.getExtraDataMap());
        dispute.setDelayedPayoutTxId(disputeFromOpener.getDelayedPayoutTxId());
        dispute.setDonationAddressOfDelayedPayoutTx(disputeFromOpener.getDonationAddressOfDelayedPayoutTx());
        dispute.setBurningManSelectionHeight(disputeFromOpener.getBurningManSelectionHeight());
        dispute.setTradeTxFee(disputeFromOpener.getTradeTxFee());

        Optional<Dispute> storedDisputeOptional = findDispute(dispute);

        // Valid case if both have opened a dispute and agent was not online.
        if (storedDisputeOptional.isPresent()) {
            log.info("We got a dispute already open for that trade and trading peer. TradeId = {}", dispute.getTradeId());
            return;
        }

        String disputeInfo = getDisputeInfo(dispute);
        String disputeMessage = getDisputeIntroForPeer(disputeInfo);
        String sysMsg = dispute.isSupportTicket() ?
                Res.get("support.peerOpenedTicket", disputeInfo, Version.VERSION)
                : disputeMessage;
        ChatMessage chatMessage = new ChatMessage(
                getSupportType(),
                dispute.getTradeId(),
                pubKeyRing.hashCode(),
                false,
                Res.get("support.systemMsg", sysMsg),
                p2PService.getAddress());
        chatMessage.setSystemMessage(true);
        dispute.addAndPersistChatMessage(chatMessage);

        disputeList.add(dispute);
        sendDisputeOpeningMsg(dispute);
    }

    public void sendDisputeOpeningMsg(Dispute dispute) {
        // We mirrored dispute already!
        ChatMessage chatMessage = dispute.getChatMessages().get(0);
        Contract contract = dispute.getContract();
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerPubKeyRing() : contract.getSellerPubKeyRing();
        NodeAddress peersNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerNodeAddress() : contract.getSellerNodeAddress();
        PeerOpenedDisputeMessage peerOpenedDisputeMessage = new PeerOpenedDisputeMessage(dispute,
                p2PService.getAddress(),
                UUID.randomUUID().toString(),
                getSupportType());

        log.info("Send {} to peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, chatMessage.uid={}",
                peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                chatMessage.getUid());

        recordPendingMessage(peerOpenedDisputeMessage.getClass().getSimpleName());
        mailboxMessageService.sendEncryptedMailboxMessage(peersNodeAddress,
                peersPubKeyRing,
                peerOpenedDisputeMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                        "chatMessage.uid={}",
                                peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                chatMessage.getUid());

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setArrived(true);
                        requestPersistence();
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                        "chatMessage.uid={}",
                                peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                chatMessage.getUid());

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setStoredInMailbox(true);
                        requestPersistence();
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                        "chatMessage.uid={}, errorMessage={}",
                                peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                chatMessage.getUid(), errorMessage);

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setSendMessageError(errorMessage);
                        requestPersistence();
                    }
                }
        );
        addPriceInfoMessage(dispute, 0);
        requestPersistence();
    }

    // dispute agent send result to trader
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String summaryText) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        ChatMessage chatMessage = new ChatMessage(
                getSupportType(),
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                false,
                summaryText,
                p2PService.getAddress());

        disputeResult.setChatMessage(chatMessage);
        dispute.addAndPersistChatMessage(chatMessage);

        NodeAddress peersNodeAddress;
        Contract contract = dispute.getContract();
        if (contract.getBuyerPubKeyRing().equals(dispute.getTraderPubKeyRing()))
            peersNodeAddress = contract.getBuyerNodeAddress();
        else
            peersNodeAddress = contract.getSellerNodeAddress();
        DisputeResultMessage disputeResultMessage = new DisputeResultMessage(disputeResult,
                p2PService.getAddress(),
                UUID.randomUUID().toString(),
                getSupportType());
        log.info("Send {} to peer {}. tradeId={}, disputeResultMessage.uid={}, chatMessage.uid={}",
                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress, disputeResultMessage.getTradeId(),
                disputeResultMessage.getUid(), chatMessage.getUid());
        recordPendingMessage(disputeResultMessage.getClass().getSimpleName());
        mailboxMessageService.sendEncryptedMailboxMessage(peersNodeAddress,
                dispute.getTraderPubKeyRing(),
                disputeResultMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "chatMessage.uid={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                chatMessage.getUid());

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setArrived(true);
                        requestPersistence();
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "chatMessage.uid={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                chatMessage.getUid());

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setStoredInMailbox(true);
                        requestPersistence();
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "chatMessage.uid={}, errorMessage={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                chatMessage.getUid(), errorMessage);

                        clearPendingMessage();
                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setSendMessageError(errorMessage);
                        requestPersistence();
                    }
                }
        );
        requestPersistence();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Tuple2<NodeAddress, PubKeyRing> getNodeAddressPubKeyRingTuple(Dispute dispute) {
        PubKeyRing receiverPubKeyRing = null;
        NodeAddress peerNodeAddress = null;
        if (isTrader(dispute)) {
            receiverPubKeyRing = dispute.getAgentPubKeyRing();
            peerNodeAddress = getAgentNodeAddress(dispute);
        } else if (isAgent(dispute)) {
            receiverPubKeyRing = dispute.getTraderPubKeyRing();
            Contract contract = dispute.getContract();
            if (contract.getBuyerPubKeyRing().equals(receiverPubKeyRing))
                peerNodeAddress = contract.getBuyerNodeAddress();
            else
                peerNodeAddress = contract.getSellerNodeAddress();
        } else {
            log.error("That must not happen. Trader cannot communicate to other trader.");
        }
        return new Tuple2<>(peerNodeAddress, receiverPubKeyRing);
    }

    public boolean isAgent(Dispute dispute) {
        return pubKeyRing.equals(dispute.getAgentPubKeyRing());
    }

    private Optional<Dispute> findDispute(Dispute dispute) {
        return findDispute(dispute.getTradeId(), dispute.getTraderId());
    }

    protected Optional<Dispute> findDispute(DisputeResult disputeResult) {
        ChatMessage chatMessage = disputeResult.getChatMessage();
        checkNotNull(chatMessage, "chatMessage must not be null");
        return findDispute(disputeResult.getTradeId(), disputeResult.getTraderId());
    }

    private Optional<Dispute> findDispute(ChatMessage message) {
        return findDispute(message.getTradeId(), message.getTraderId());
    }

    protected Optional<Dispute> findDispute(String tradeId, int traderId) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return Optional.empty();
        }
        return disputeList.stream()
                .filter(e -> e.getTradeId().equals(tradeId) && e.getTraderId() == traderId)
                .findAny();
    }

    public Optional<Dispute> findDispute(String tradeId) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return Optional.empty();
        }
        return disputeList.stream()
                .filter(e -> e.getTradeId().equals(tradeId))
                .findAny();
    }

    public Optional<Trade> findTrade(Dispute dispute) {
        Optional<Trade> retVal = tradeManager.getTradeById(dispute.getTradeId());
        if (retVal.isEmpty()) {
            retVal = closedTradableManager.getClosedTrades().stream().filter(e -> e.getId().equals(dispute.getTradeId())).findFirst();
        }
        return retVal;
    }

    private void addMediationResultMessage(Dispute dispute) {
        // In case of refundAgent we add a message with the mediatorsDisputeSummary. Only visible for refundAgent.
        if (dispute.getMediatorsDisputeResult() != null) {
            String mediatorsDisputeResult = Res.get("support.mediatorsDisputeSummary", dispute.getMediatorsDisputeResult());
            ChatMessage mediatorsDisputeResultMessage = new ChatMessage(
                    getSupportType(),
                    dispute.getTradeId(),
                    pubKeyRing.hashCode(),
                    false,
                    mediatorsDisputeResult,
                    p2PService.getAddress());
            mediatorsDisputeResultMessage.setSystemMessage(true);
            dispute.addAndPersistChatMessage(mediatorsDisputeResultMessage);
            requestPersistence();
        }
    }

    // when a mediated trade changes, send a system message informing the mediator, so they can maybe close their ticket.
    public void disputedTradeUpdate(String message, Dispute dispute, boolean close) {
        if (dispute.isClosed()) {
            return;
        }
        ChatMessage chatMessage = new ChatMessage(
                getSupportType(),
                dispute.getTradeId(),
                dispute.getTraderId(),
                true,
                Res.get("support.info.disputedTradeUpdate", message),
                p2PService.getAddress());
        chatMessage.setSystemMessage(true);
        this.sendChatMessage(chatMessage);  // inform the mediator
        if (close) {
            dispute.setIsClosed();              // close the trader's ticket
        }
        requestPersistence();
    }

    protected void addMediationLogsReceivedMessage(Dispute dispute, String logsIdentifier) {
        String logsReceivedMessage = Res.get("support.mediatorReceivedLogs", logsIdentifier);
        ChatMessage chatMessage = new ChatMessage(
                getSupportType(),
                dispute.getTradeId(),
                pubKeyRing.hashCode(),
                false,
                logsReceivedMessage,
                p2PService.getAddress());
        chatMessage.setSystemMessage(true);
        dispute.addAndPersistChatMessage(chatMessage);
        requestPersistence();
    }

    // If price was going down between take offer time and open dispute time the buyer has an incentive to
    // not send the payment but to try to make a new trade with the better price. We risks to lose part of the
    // security deposit (in mediation we will always get back 0.003 BTC to keep some incentive to accept mediated
    // proposal). But if gain is larger than this loss he has economically an incentive to default in the trade.
    // We do all those calculations to give a hint to mediators to detect option trades.
    protected void addPriceInfoMessage(Dispute dispute, int counter) {
        if (!priceFeedService.hasPrices()) {
            if (counter < 3) {
                log.info("Price provider has still no data. This is expected at startup. We try again in 10 sec.");
                UserThread.runAfter(() -> addPriceInfoMessage(dispute, counter + 1), 10);
            } else {
                log.warn("Price provider still has no data after 3 repeated requests and 30 seconds delay. We give up.");
            }
            return;
        }

        Contract contract = dispute.getContract();
        OfferPayload offerPayload = contract.getOfferPayload();
        Price priceAtDisputeOpening = getPrice(offerPayload.getCurrencyCode());
        if (priceAtDisputeOpening == null) {
            log.info("Price provider did not provide a price for {}. " +
                            "This is expected if this currency is not supported by the price providers.",
                    offerPayload.getCurrencyCode());
            return;
        }

        // The amount we would get if we do a new trade with current price
        Coin potentialAmountAtDisputeOpening = priceAtDisputeOpening.getAmountByVolume(contract.getTradeVolume());
        Coin buyerSecurityDeposit = Coin.valueOf(offerPayload.getBuyerSecurityDeposit());
        Coin minRefundAtMediatedDispute = Restrictions.getMinRefundAtMediatedDispute(contract.getTradeAmount());
        // minRefundAtMediatedDispute is always larger as buyerSecurityDeposit at mediated payout, we ignore refund agent case here as there it can be 0.
        Coin maxLossSecDeposit = buyerSecurityDeposit.subtract(minRefundAtMediatedDispute);
        Coin tradeAmount = contract.getTradeAmount();
        Coin potentialGain = potentialAmountAtDisputeOpening.subtract(tradeAmount).subtract(maxLossSecDeposit);
        String optionTradeDetails;
        // We don't translate those strings (yet) as it is only displayed to mediators/arbitrators.
        String headline;
        if (potentialGain.isPositive()) {
            headline = "This might be a potential option trade!";
            optionTradeDetails = "\nBTC amount calculated with price at dispute opening: " + potentialAmountAtDisputeOpening.toFriendlyString() +
                    "\nMax loss of security deposit is: " + maxLossSecDeposit.toFriendlyString() +
                    "\nPossible gain from an option trade is: " + potentialGain.toFriendlyString();
        } else {
            headline = "It does not appear to be an option trade.";
            optionTradeDetails = "\nBTC amount calculated with price at dispute opening: " + potentialAmountAtDisputeOpening.toFriendlyString() +
                    "\nMax loss of security deposit is: " + maxLossSecDeposit.toFriendlyString() +
                    "\nPossible loss from an option trade is: " + potentialGain.multiply(-1).toFriendlyString();
        }

        String percentagePriceDetails = offerPayload.isUseMarketBasedPrice() ?
                " (market based price was used: " + offerPayload.getMarketPriceMargin() * 100 + "%)" :
                " (fix price was used)";

        String priceInfoText = "System message: " + headline +
                "\n\nTrade price: " + contract.getTradePrice().toFriendlyString() + percentagePriceDetails +
                "\nTrade amount: " + tradeAmount.toFriendlyString() +
                "\nPrice at dispute opening: " + priceAtDisputeOpening.toFriendlyString() +
                optionTradeDetails;

        // We use the existing msg to copy over the users data
        ChatMessage priceInfoMessage = new ChatMessage(
                getSupportType(),
                dispute.getTradeId(),
                pubKeyRing.hashCode(),
                false,
                priceInfoText,
                p2PService.getAddress());
        priceInfoMessage.setSystemMessage(true);
        dispute.addAndPersistChatMessage(priceInfoMessage);
        requestPersistence();
    }

    @Nullable
    private Price getPrice(String currencyCode) {
        MarketPrice marketPrice = priceFeedService.getMarketPrice(currencyCode);
        if (marketPrice != null && marketPrice.isRecentExternalPriceAvailable()) {
            double marketPriceAsDouble = marketPrice.getPrice();
            try {
                int precision = CurrencyUtil.isCryptoCurrency(currencyCode) ?
                        Altcoin.SMALLEST_UNIT_EXPONENT :
                        Fiat.SMALLEST_UNIT_EXPONENT;
                double scaled = MathUtils.scaleUpByPowerOf10(marketPriceAsDouble, precision);
                long roundedToLong = MathUtils.roundDoubleToLong(scaled);
                return Price.valueOf(currencyCode, roundedToLong);
            } catch (Exception e) {
                log.error("Exception at getPrice / parseToFiat: " + e);
                return null;
            }
        } else {
            return null;
        }
    }

    public boolean hasPendingMessageAtShutdown() {
        if (pendingOutgoingMessage.length() > 0) {
            log.warn("{} has an outgoing message pending: {}", this.getClass().getSimpleName(), pendingOutgoingMessage);
            return true;
        }
        return false;
    }

    private void recordPendingMessage(String className) {
        pendingOutgoingMessage = className;
    }

    private void clearPendingMessage() {
        pendingOutgoingMessage = "";
    }
}
