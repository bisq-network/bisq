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
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOfferManager;
import bisq.core.support.SupportManager;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.dispute.messages.OpenNewDisputeMessage;
import bisq.core.support.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.trade.Contract;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.app.Version;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;

import javafx.beans.property.IntegerProperty;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public abstract class DisputeManager<T extends DisputeList<? extends DisputeList>> extends SupportManager {
    protected final TradeWalletService tradeWalletService;
    protected final BtcWalletService btcWalletService;
    protected final TradeManager tradeManager;
    protected final ClosedTradableManager closedTradableManager;
    protected final OpenOfferManager openOfferManager;
    protected final PubKeyRing pubKeyRing;
    protected final DisputeListService<T> disputeListService;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public DisputeManager(P2PService p2PService,
                          TradeWalletService tradeWalletService,
                          BtcWalletService btcWalletService,
                          WalletsSetup walletsSetup,
                          TradeManager tradeManager,
                          ClosedTradableManager closedTradableManager,
                          OpenOfferManager openOfferManager,
                          PubKeyRing pubKeyRing,
                          DisputeListService<T> disputeListService) {
        super(p2PService, walletsSetup);

        this.tradeWalletService = tradeWalletService;
        this.btcWalletService = btcWalletService;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.openOfferManager = openOfferManager;
        this.pubKeyRing = pubKeyRing;
        this.disputeListService = disputeListService;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void persist() {
        disputeListService.persist();
    }

    @Override
    public NodeAddress getPeerNodeAddress(ChatMessage message) {
        Optional<Dispute> disputeOptional = findDispute(message);
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }
        return getNodeAddressPubKeyRingTuple(disputeOptional.get()).first;
    }

    @Override
    public PubKeyRing getPeerPubKeyRing(ChatMessage message) {
        Optional<Dispute> disputeOptional = findDispute(message);
        if (!disputeOptional.isPresent()) {
            log.warn("Could not find dispute for tradeId = {} traderId = {}",
                    message.getTradeId(), message.getTraderId());
            return null;
        }

        return getNodeAddressPubKeyRingTuple(disputeOptional.get()).second;
    }

    @Override
    public List<ChatMessage> getAllChatMessages() {
        return getDisputeList().stream()
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

    protected abstract Trade.DisputeState getDisputeState_StartedByPeer();

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

    public Storage<? extends DisputeList> getStorage() {
        return disputeListService.getStorage();
    }

    public ObservableList<Dispute> getDisputesAsObservableList() {
        return disputeListService.getDisputesAsObservableList();
    }

    public String getNrOfDisputes(boolean isBuyer, Contract contract) {
        return disputeListService.getNrOfDisputes(isBuyer, contract);
    }

    private T getDisputeList() {
        return disputeListService.getDisputeList();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void onAllServicesInitialized() {
        super.onAllServicesInitialized();
        disputeListService.onAllServicesInitialized();

        p2PService.addP2PServiceListener(new BootstrapListener() {
            @Override
            public void onUpdatedDataReceived() {
                tryApplyMessages();
            }
        });

        walletsSetup.downloadPercentageProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.isDownloadComplete())
                tryApplyMessages();
        });

        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.hasSufficientPeersForBroadcast())
                tryApplyMessages();
        });

        tryApplyMessages();
        cleanupDisputes();
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

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Message handler
    ///////////////////////////////////////////////////////////////////////////////////////////

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

        dispute.setStorage(disputeListService.getStorage());
        Contract contractFromOpener = dispute.getContract();
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contractFromOpener.getSellerPubKeyRing() : contractFromOpener.getBuyerPubKeyRing();
        if (isAgent(dispute)) {
            if (!disputeList.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (!storedDisputeOptional.isPresent()) {
                    disputeList.add(dispute);
                    errorMessage = sendPeerOpenedDisputeMessage(dispute, contractFromOpener, peersPubKeyRing);
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
            PubKeyRing sendersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contractFromOpener.getBuyerPubKeyRing() : contractFromOpener.getSellerPubKeyRing();
            sendAckMessage(chatMessage, sendersPubKeyRing, errorMessage == null, errorMessage);
        }

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
        }
    }

    // not dispute requester receives that from dispute agent
    protected void onPeerOpenedDisputeMessage(PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return;
        }

        String errorMessage = null;
        Dispute dispute = peerOpenedDisputeMessage.getDispute();
        if (!isAgent(dispute)) {
            if (!disputeList.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (!storedDisputeOptional.isPresent()) {
                    dispute.setStorage(disputeListService.getStorage());
                    disputeList.add(dispute);
                    Optional<Trade> tradeOptional = tradeManager.getTradeById(dispute.getTradeId());
                    tradeOptional.ifPresent(trade -> trade.setDisputeState(getDisputeState_StartedByPeer()));
                    errorMessage = null;
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
        if (!storedDisputeOptional.isPresent() || reOpen) {
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

            p2PService.sendEncryptedMailboxMessage(agentNodeAddress,
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

                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setArrived(true);
                            disputeList.persist();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), agentNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    chatMessage.getUid());

                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setStoredInMailbox(true);
                            disputeList.persist();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}, errorMessage={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), agentNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    chatMessage.getUid(), errorMessage);

                            // We use the chatMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setSendMessageError(errorMessage);
                            disputeList.persist();
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
    }

    // dispute agent sends that to trading peer when he received openDispute request
    private String sendPeerOpenedDisputeMessage(Dispute disputeFromOpener,
                                                Contract contractFromOpener,
                                                PubKeyRing pubKeyRing) {
        T disputeList = getDisputeList();
        if (disputeList == null) {
            log.warn("disputes is null");
            return null;
        }

        Dispute dispute = new Dispute(disputeListService.getStorage(),
                disputeFromOpener.getTradeId(),
                pubKeyRing.hashCode(),
                !disputeFromOpener.isDisputeOpenerIsBuyer(),
                !disputeFromOpener.isDisputeOpenerIsMaker(),
                pubKeyRing,
                disputeFromOpener.getTradeDate().getTime(),
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
        dispute.setDelayedPayoutTxId(disputeFromOpener.getDelayedPayoutTxId());

        Optional<Dispute> storedDisputeOptional = findDispute(dispute);
        if (!storedDisputeOptional.isPresent()) {
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

            // we mirrored dispute already!
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

            p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
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

                            // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setArrived(true);
                            disputeList.persist();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}",
                                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                    chatMessage.getUid());

                            // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setStoredInMailbox(true);
                            disputeList.persist();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                            "chatMessage.uid={}, errorMessage={}",
                                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                    chatMessage.getUid(), errorMessage);

                            // We use the chatMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            chatMessage.setSendMessageError(errorMessage);
                            disputeList.persist();
                        }
                    }
            );
            return null;
        } else {
            // valid case if both have opened a dispute and agent was not online.
            log.debug("We got a dispute already open for that trade and trading peer. TradeId = {}",
                    dispute.getTradeId());
            return null;
        }
    }

    // dispute agent send result to trader
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String text) {
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
                text,
                p2PService.getAddress());

        dispute.addAndPersistChatMessage(chatMessage);
        disputeResult.setChatMessage(chatMessage);

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
        p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
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

                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setArrived(true);
                        disputeList.persist();
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "chatMessage.uid={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                chatMessage.getUid());

                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setStoredInMailbox(true);
                        disputeList.persist();
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "chatMessage.uid={}, errorMessage={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                chatMessage.getUid(), errorMessage);

                        // We use the chatMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        chatMessage.setSendMessageError(errorMessage);
                        disputeList.persist();
                    }
                }
        );
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

    private boolean isAgent(Dispute dispute) {
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

    private Optional<Dispute> findDispute(String tradeId, int traderId) {
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
}
