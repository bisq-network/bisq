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

package bisq.core.dispute;

import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.chat.ChatManager;
import bisq.core.dispute.messages.DisputeCommunicationMessage;
import bisq.core.dispute.messages.DisputeResultMessage;
import bisq.core.dispute.messages.OpenNewDisputeMessage;
import bisq.core.dispute.messages.PeerOpenedDisputeMessage;
import bisq.core.dispute.messages.PeerPublishedDisputePayoutTxMessage;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Contract;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;

import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;
import bisq.network.p2p.SendMailboxMessageListener;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Version;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.handlers.FaultHandler;
import bisq.common.handlers.ResultHandler;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.inject.Inject;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkNotNull;

@Slf4j
public class DisputeManager implements PersistedDataHost {
    private final TradeWalletService tradeWalletService;
    private final BtcWalletService walletService;
    private final WalletsSetup walletsSetup;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final OpenOfferManager openOfferManager;
    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final Storage<DisputeList> disputeStorage;
    @Nullable
    @Getter
    private DisputeList disputes;
    private final Map<String, Dispute> openDisputes;
    private final Map<String, Dispute> closedDisputes;
    private final Map<String, Timer> delayMsgMap = new HashMap<>();

    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    @Getter
    private final IntegerProperty numOpenDisputes = new SimpleIntegerProperty();
    @Getter
    private final ChatManager chatManager;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeManager(P2PService p2PService,
                          TradeWalletService tradeWalletService,
                          BtcWalletService walletService,
                          WalletsSetup walletsSetup,
                          TradeManager tradeManager,
                          ClosedTradableManager closedTradableManager,
                          OpenOfferManager openOfferManager,
                          KeyRing keyRing,
                          Storage<DisputeList> storage) {
        this.p2PService = p2PService;
        this.tradeWalletService = tradeWalletService;
        this.walletService = walletService;
        this.walletsSetup = walletsSetup;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.openOfferManager = openOfferManager;
        this.keyRing = keyRing;

        chatManager = new ChatManager(p2PService, walletsSetup);
        chatManager.setChatSession(new DisputeChatSession(null, this));

        disputeStorage = storage;

        openDisputes = new HashMap<>();
        closedDisputes = new HashMap<>();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        disputes = new DisputeList(disputeStorage);
        disputes.readPersisted();
        disputes.stream().forEach(dispute -> dispute.setStorage(disputeStorage));
    }

    public void onAllServicesInitialized() {
        chatManager.onAllServicesInitialized();
        p2PService.addP2PServiceListener(new BootstrapListener() {
            @Override
            public void onUpdatedDataReceived() {
                chatManager.tryApplyMessages();
            }
        });

        walletsSetup.downloadPercentageProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.isDownloadComplete())
                chatManager.tryApplyMessages();
        });

        walletsSetup.numPeersProperty().addListener((observable, oldValue, newValue) -> {
            if (walletsSetup.hasSufficientPeersForBroadcast())
                chatManager.tryApplyMessages();
        });

        chatManager.tryApplyMessages();

        cleanupDisputes();

        if (disputes != null) {
            disputes.getList().addListener((ListChangeListener<Dispute>) change -> {
                change.next();
                onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
            });
            onDisputesChangeListener(disputes.getList(), null);
        } else {
            log.warn("disputes is null");
        }
    }

    private void onDisputesChangeListener(List<? extends Dispute> addedList,
                                          @Nullable List<? extends Dispute> removedList) {
        if (removedList != null) {
            removedList.forEach(dispute -> {
                String id = dispute.getId();
                if (disputeIsClosedSubscriptionsMap.containsKey(id)) {
                    disputeIsClosedSubscriptionsMap.get(id).unsubscribe();
                    disputeIsClosedSubscriptionsMap.remove(id);
                }
            });
        }
        addedList.forEach(dispute -> {
            String id = dispute.getId();
            Subscription disputeStateSubscription = EasyBind.subscribe(dispute.isClosedProperty(),
                    isClosed -> {
                        if (disputes != null) {
                            // We get the event before the list gets updated, so we execute on next frame
                            UserThread.execute(() -> {
                                int openDisputes = (int) disputes.getList().stream()
                                        .filter(e -> !e.isClosed()).count();
                                numOpenDisputes.set(openDisputes);
                            });
                        }
                    });
            disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
        });
    }

    public void cleanupDisputes() {
        if (disputes != null) {
            disputes.stream().forEach(dispute -> {
                dispute.setStorage(disputeStorage);
                if (dispute.isClosed())
                    closedDisputes.put(dispute.getTradeId(), dispute);
                else
                    openDisputes.put(dispute.getTradeId(), dispute);
            });
        } else {
            log.warn("disputes is null");
        }

        // If we have duplicate disputes we close the second one (might happen if both traders opened a dispute and arbitrator
        // was offline, so could not forward msg to other peer, then the arbitrator might have 4 disputes open for 1 trade)
        openDisputes.forEach((key, openDispute) -> {
            if (closedDisputes.containsKey(key)) {
                Dispute closedDispute = closedDisputes.get(key);
                // We need to check if is from the same peer, we don't want to close the peers dispute
                if (closedDispute.getTraderId() == openDispute.getTraderId()) {
                    openDispute.setIsClosed(true);
                    tradeManager.closeDisputedTrade(openDispute.getTradeId());
                }
            }
        });
    }

    public void sendOpenNewDisputeMessage(Dispute dispute,
                                          boolean reOpen,
                                          ResultHandler resultHandler,
                                          FaultHandler faultHandler) {
        if (disputes == null) {
            log.warn("disputes is null");
            return;
        }

        if (disputes.contains(dispute)) {
            String msg = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
            log.warn(msg);
            faultHandler.handleFault(msg, new DisputeAlreadyOpenException());
            return;
        }

        Optional<Dispute> storedDisputeOptional = findDispute(dispute);
        if (!storedDisputeOptional.isPresent() || reOpen) {
            String disputeInfo = getDisputeInfo(dispute.isMediationDispute());
            String sysMsg = dispute.isSupportTicket() ?
                    Res.get("support.youOpenedTicket", disputeInfo, Version.VERSION)
                    : Res.get("support.youOpenedDispute", disputeInfo, Version.VERSION);

            DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                    DisputeCommunicationMessage.Type.DISPUTE,
                    dispute.getTradeId(),
                    keyRing.getPubKeyRing().hashCode(),
                    false,
                    Res.get("support.systemMsg", sysMsg),
                    p2PService.getAddress(),
                    dispute.isMediationDispute()
            );
            disputeCommunicationMessage.setSystemMessage(true);
            dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
            if (!reOpen) {
                disputes.add(dispute);
            }

            NodeAddress conflictResolverNodeAddress = dispute.getConflictResolverNodeAddress();
            OpenNewDisputeMessage openNewDisputeMessage = new OpenNewDisputeMessage(dispute, p2PService.getAddress(),
                    UUID.randomUUID().toString());
            log.info("Send {} to peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                            "disputeCommunicationMessage.uid={}",
                    openNewDisputeMessage.getClass().getSimpleName(), conflictResolverNodeAddress,
                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                    disputeCommunicationMessage.getUid());
            p2PService.sendEncryptedMailboxMessage(conflictResolverNodeAddress,
                    dispute.getConflictResolverPubKeyRing(),
                    openNewDisputeMessage,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), conflictResolverNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid());

                            // We use the disputeCommunicationMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setArrived(true);
                            disputes.persist();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), conflictResolverNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid());

                            // We use the disputeCommunicationMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setStoredInMailbox(true);
                            disputes.persist();
                            resultHandler.handleResult();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}, errorMessage={}",
                                    openNewDisputeMessage.getClass().getSimpleName(), conflictResolverNodeAddress,
                                    openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid(), errorMessage);

                            // We use the disputeCommunicationMessage wrapped inside the openNewDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setSendMessageError(errorMessage);
                            disputes.persist();
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

    private String getDisputeInfo(boolean isMediationDispute) {
        String role = isMediationDispute ? Res.get("shared.mediator").toLowerCase() :
                Res.get("shared.arbitrator2").toLowerCase();
        String link = isMediationDispute ? "https://docs.bisq.network/trading-rules.html#mediation" :
                "https://bisq.network/docs/exchange/arbitration-system";
        return Res.get("support.initialInfo", role, role, link);
    }

    // arbitrator sends that to trading peer when he received openDispute request
    private String sendPeerOpenedDisputeMessage(Dispute disputeFromOpener,
                                                Contract contractFromOpener,
                                                PubKeyRing pubKeyRing) {
        if (disputes == null) {
            log.warn("disputes is null");
            return null;
        }

        Dispute dispute = new Dispute(disputeStorage,
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
                disputeFromOpener.getConflictResolverPubKeyRing(),
                disputeFromOpener.isSupportTicket(),
                disputeFromOpener.isMediationDispute());
        Optional<Dispute> storedDisputeOptional = findDispute(dispute);
        if (!storedDisputeOptional.isPresent()) {
            String disputeInfo = getDisputeInfo(dispute.isMediationDispute());
            String sysMsg = dispute.isSupportTicket() ?
                    Res.get("support.peerOpenedTicket", disputeInfo)
                    : Res.get("support.peerOpenedDispute", disputeInfo);
            DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                    DisputeCommunicationMessage.Type.DISPUTE,
                    dispute.getTradeId(),
                    keyRing.getPubKeyRing().hashCode(),
                    false,
                    Res.get("support.systemMsg", sysMsg),
                    p2PService.getAddress(),
                    dispute.isMediationDispute()
            );
            disputeCommunicationMessage.setSystemMessage(true);
            dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
            disputes.add(dispute);

            // we mirrored dispute already!
            Contract contract = dispute.getContract();
            PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerPubKeyRing() : contract.getSellerPubKeyRing();
            NodeAddress peersNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerNodeAddress() : contract.getSellerNodeAddress();
            PeerOpenedDisputeMessage peerOpenedDisputeMessage = new PeerOpenedDisputeMessage(dispute,
                    p2PService.getAddress(),
                    UUID.randomUUID().toString());
            log.info("Send {} to peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                            "disputeCommunicationMessage.uid={}",
                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                    disputeCommunicationMessage.getUid());
            p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
                    peersPubKeyRing,
                    peerOpenedDisputeMessage,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}",
                                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid());

                            // We use the disputeCommunicationMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setArrived(true);
                            disputes.persist();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}",
                                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid());

                            // We use the disputeCommunicationMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setStoredInMailbox(true);
                            disputes.persist();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, peerOpenedDisputeMessage.uid={}, " +
                                            "disputeCommunicationMessage.uid={}, errorMessage={}",
                                    peerOpenedDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                    peerOpenedDisputeMessage.getTradeId(), peerOpenedDisputeMessage.getUid(),
                                    disputeCommunicationMessage.getUid(), errorMessage);

                            // We use the disputeCommunicationMessage wrapped inside the peerOpenedDisputeMessage for
                            // the state, as that is displayed to the user and we only persist that msg
                            disputeCommunicationMessage.setSendMessageError(errorMessage);
                            disputes.persist();
                        }
                    }
            );
            return null;
        } else {
            String msg = "We got a dispute already open for that trade and trading peer.\n" +
                    "TradeId = " + dispute.getTradeId();
            log.warn(msg);
            return msg;
        }
    }

    // arbitrator send result to trader
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String text) {
        if (disputes == null) {
            log.warn("disputes is null");
            return;
        }

        DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                DisputeCommunicationMessage.Type.DISPUTE,
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                false,
                text,
                p2PService.getAddress(),
                dispute.isMediationDispute()
        );

        dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
        disputeResult.setDisputeCommunicationMessage(disputeCommunicationMessage);

        NodeAddress peersNodeAddress;
        Contract contract = dispute.getContract();
        if (contract.getBuyerPubKeyRing().equals(dispute.getTraderPubKeyRing()))
            peersNodeAddress = contract.getBuyerNodeAddress();
        else
            peersNodeAddress = contract.getSellerNodeAddress();
        DisputeResultMessage disputeResultMessage = new DisputeResultMessage(disputeResult, p2PService.getAddress(),
                UUID.randomUUID().toString());
        log.info("Send {} to peer {}. tradeId={}, disputeResultMessage.uid={}, disputeCommunicationMessage.uid={}",
                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress, disputeResultMessage.getTradeId(),
                disputeResultMessage.getUid(), disputeCommunicationMessage.getUid());
        p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
                dispute.getTraderPubKeyRing(),
                disputeResultMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "disputeCommunicationMessage.uid={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                disputeCommunicationMessage.getUid());

                        // We use the disputeCommunicationMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        disputeCommunicationMessage.setArrived(true);
                        disputes.persist();
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "disputeCommunicationMessage.uid={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                disputeCommunicationMessage.getUid());

                        // We use the disputeCommunicationMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        disputeCommunicationMessage.setStoredInMailbox(true);
                        disputes.persist();
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, disputeResultMessage.uid={}, " +
                                        "disputeCommunicationMessage.uid={}, errorMessage={}",
                                disputeResultMessage.getClass().getSimpleName(), peersNodeAddress,
                                disputeResultMessage.getTradeId(), disputeResultMessage.getUid(),
                                disputeCommunicationMessage.getUid(), errorMessage);

                        // We use the disputeCommunicationMessage wrapped inside the disputeResultMessage for
                        // the state, as that is displayed to the user and we only persist that msg
                        disputeCommunicationMessage.setSendMessageError(errorMessage);
                        disputes.persist();
                    }
                }
        );
    }

    // winner (or buyer in case of 50/50) sends tx to other peer
    private void sendPeerPublishedPayoutTxMessage(Transaction transaction, Dispute dispute, Contract contract) {
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();
        NodeAddress peersNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerNodeAddress() : contract.getBuyerNodeAddress();
        log.trace("sendPeerPublishedPayoutTxMessage to peerAddress " + peersNodeAddress);
        PeerPublishedDisputePayoutTxMessage message = new PeerPublishedDisputePayoutTxMessage(transaction.bitcoinSerialize(),
                dispute.getTradeId(),
                p2PService.getAddress(),
                UUID.randomUUID().toString());
        log.info("Send {} to peer {}. tradeId={}, uid={}",
                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
        p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
                peersPubKeyRing,
                message,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                    }
                }
        );
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    // arbitrator receives that from trader who opens dispute
    void onOpenNewDisputeMessage(OpenNewDisputeMessage openNewDisputeMessage) {
        if (disputes == null) {
            log.warn("disputes is null");
            return;
        }

        String errorMessage;
        Dispute dispute = openNewDisputeMessage.getDispute();
        Contract contractFromOpener = dispute.getContract();
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contractFromOpener.getSellerPubKeyRing() : contractFromOpener.getBuyerPubKeyRing();
        if (isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (!storedDisputeOptional.isPresent()) {
                    dispute.setStorage(disputeStorage);
                    disputes.add(dispute);
                    errorMessage = sendPeerOpenedDisputeMessage(dispute, contractFromOpener, peersPubKeyRing);
                } else {
                    errorMessage = "We got a dispute already open for that trade and trading peer.\n" +
                            "TradeId = " + dispute.getTradeId();
                    log.warn(errorMessage);
                }
            } else {
                errorMessage = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
                log.warn(errorMessage);
            }
        } else {
            errorMessage = "Trader received openNewDisputeMessage. That must never happen.";
            log.error(errorMessage);
        }

        // We use the DisputeCommunicationMessage not the openNewDisputeMessage for the ACK
        ObservableList<DisputeCommunicationMessage> messages = openNewDisputeMessage.getDispute().getDisputeCommunicationMessages();
        if (!messages.isEmpty()) {
            DisputeCommunicationMessage msg = messages.get(0);
            PubKeyRing sendersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contractFromOpener.getBuyerPubKeyRing() : contractFromOpener.getSellerPubKeyRing();
            chatManager.sendAckMessage(msg, sendersPubKeyRing, errorMessage == null, errorMessage);
        }
    }

    // not dispute requester receives that from arbitrator
    void onPeerOpenedDisputeMessage(PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        if (disputes == null) {
            log.warn("disputes is null");
            return;
        }

        String errorMessage;
        Dispute dispute = peerOpenedDisputeMessage.getDispute();
        if (!isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                Optional<Dispute> storedDisputeOptional = findDispute(dispute);
                if (!storedDisputeOptional.isPresent()) {
                    dispute.setStorage(disputeStorage);
                    disputes.add(dispute);
                    Optional<Trade> tradeOptional = tradeManager.getTradeById(dispute.getTradeId());
                    tradeOptional.ifPresent(trade -> trade.setDisputeState(Trade.DisputeState.DISPUTE_STARTED_BY_PEER));
                    errorMessage = null;
                } else {
                    errorMessage = "We got a dispute already open for that trade and trading peer.\n" +
                            "TradeId = " + dispute.getTradeId();
                    log.warn(errorMessage);
                }
            } else {
                errorMessage = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
                log.warn(errorMessage);
            }
        } else {
            errorMessage = "Arbitrator received peerOpenedDisputeMessage. That must never happen.";
            log.error(errorMessage);
        }

        // We use the DisputeCommunicationMessage not the peerOpenedDisputeMessage for the ACK
        ObservableList<DisputeCommunicationMessage> messages = peerOpenedDisputeMessage.getDispute().getDisputeCommunicationMessages();
        if (!messages.isEmpty()) {
            DisputeCommunicationMessage msg = messages.get(0);
            chatManager.sendAckMessage(msg, dispute.getConflictResolverPubKeyRing(), errorMessage == null, errorMessage);
        }

        chatManager.sendAckMessage(peerOpenedDisputeMessage, dispute.getConflictResolverPubKeyRing(), errorMessage == null, errorMessage);
    }

    // We get that message at both peers. The dispute object is in context of the trader
    void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        DisputeCommunicationMessage disputeCommunicationMessage = disputeResult.getDisputeCommunicationMessage();
        checkNotNull(disputeCommunicationMessage, "disputeCommunicationMessage must not be null");
        if (!disputeCommunicationMessage.isMediationDispute() &&
                Arrays.equals(disputeResult.getArbitratorPubKey(),
                        walletService.getArbitratorAddressEntry().getPubKey())) {
            log.error("Arbitrator received disputeResultMessage. That must never happen.");
            return;
        }

        String tradeId = disputeResult.getTradeId();
        Optional<Dispute> disputeOptional = findDispute(disputeResult);
        String uid = disputeResultMessage.getUid();
        if (!disputeOptional.isPresent()) {
            log.warn("We got a dispute result msg but we don't have a matching dispute. " +
                    "That might happen when we get the disputeResultMessage before the dispute was created. " +
                    "We try again after 2 sec. to apply the disputeResultMessage. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 2 sec. to be sure the comm. msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        // try {
        cleanupRetryMap(uid);
        if (!dispute.getDisputeCommunicationMessages().contains(disputeCommunicationMessage)) {
            dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
        } else {
            log.warn("We got a dispute mail msg what we have already stored. TradeId = " + disputeCommunicationMessage.getTradeId());
        }
        dispute.setIsClosed(true);

        if (dispute.disputeResultProperty().get() != null) {
            log.warn("We got already a dispute result. That should only happen if a dispute needs to be closed " +
                    "again because the first close did not succeed. TradeId = " + tradeId);
        }

        dispute.setDisputeResult(disputeResult);

        Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
        if (dispute.isMediationDispute()) {
            // Mediation case
            if (tradeOptional.isPresent()) {
                tradeOptional.get().setDisputeState(Trade.DisputeState.MEDIATION_CLOSED);
            } else {
                Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
                openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
            }
            chatManager.sendAckMessage(disputeCommunicationMessage, dispute.getConflictResolverPubKeyRing(), true, null);
            return;
        }

        // Arbitration case
        String errorMessage = null;
        boolean success = false;
        try {
            // We need to avoid publishing the tx from both traders as it would create problems with zero confirmation withdrawals
            // There would be different transactions if both sign and publish (signers: once buyer+arb, once seller+arb)
            // The tx publisher is the winner or in case both get 50% the buyer, as the buyer has more inventive to publish the tx as he receives
            // more BTC as he has deposited
            Contract contract = dispute.getContract();

            boolean isBuyer = keyRing.getPubKeyRing().equals(contract.getBuyerPubKeyRing());
            DisputeResult.Winner publisher = disputeResult.getWinner();

            // Sometimes the user who receives the trade amount is never online, so we might want to
            // let the loser publish the tx. When the winner comes online he gets his funds as it was published by the other peer.
            // Default isLoserPublisher is set to false
            if (disputeResult.isLoserPublisher()) {
                // we invert the logic
                if (publisher == DisputeResult.Winner.BUYER)
                    publisher = DisputeResult.Winner.SELLER;
                else if (publisher == DisputeResult.Winner.SELLER)
                    publisher = DisputeResult.Winner.BUYER;
            }

            if ((isBuyer && publisher == DisputeResult.Winner.BUYER)
                    || (!isBuyer && publisher == DisputeResult.Winner.SELLER)) {

                Transaction payoutTx = null;
                if (tradeOptional.isPresent()) {
                    payoutTx = tradeOptional.get().getPayoutTx();
                } else {
                    Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(tradeId);
                    if (tradableOptional.isPresent() && tradableOptional.get() instanceof Trade) {
                        payoutTx = ((Trade) tradableOptional.get()).getPayoutTx();
                    }
                }

                if (payoutTx == null) {
                    if (dispute.getDepositTxSerialized() != null) {
                        byte[] multiSigPubKey = isBuyer ? contract.getBuyerMultiSigPubKey() : contract.getSellerMultiSigPubKey();
                        DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(tradeId, multiSigPubKey);
                        Transaction signedDisputedPayoutTx = tradeWalletService.traderSignAndFinalizeDisputedPayoutTx(
                                dispute.getDepositTxSerialized(),
                                disputeResult.getArbitratorSignature(),
                                disputeResult.getBuyerPayoutAmount(),
                                disputeResult.getSellerPayoutAmount(),
                                contract.getBuyerPayoutAddressString(),
                                contract.getSellerPayoutAddressString(),
                                multiSigKeyPair,
                                contract.getBuyerMultiSigPubKey(),
                                contract.getSellerMultiSigPubKey(),
                                disputeResult.getArbitratorPubKey()
                        );
                        Transaction committedDisputedPayoutTx = tradeWalletService.addTxToWallet(signedDisputedPayoutTx);
                        tradeWalletService.broadcastTx(committedDisputedPayoutTx, new TxBroadcaster.Callback() {
                            @Override
                            public void onSuccess(Transaction transaction) {
                                // after successful publish we send peer the tx
                                dispute.setDisputePayoutTxId(transaction.getHashAsString());
                                sendPeerPublishedPayoutTxMessage(transaction, dispute, contract);
                                updateTradeOrOpenOfferManager(tradeId);
                            }

                            @Override
                            public void onFailure(TxBroadcastException exception) {
                                log.error(exception.getMessage());
                            }
                        }, 15);

                        success = true;
                    } else {
                        errorMessage = "DepositTx is null. TradeId = " + tradeId;
                        log.warn(errorMessage);
                        success = false;
                    }
                } else {
                    log.warn("We got already a payout tx. That might be the case if the other peer did not get the " +
                            "payout tx and opened a dispute. TradeId = " + tradeId);
                    dispute.setDisputePayoutTxId(payoutTx.getHashAsString());
                    sendPeerPublishedPayoutTxMessage(payoutTx, dispute, contract);

                    success = true;
                }
            } else {
                log.trace("We don't publish the tx as we are not the winning party.");
                // Clean up tangling trades
                if (dispute.disputeResultProperty().get() != null && dispute.isClosed()) {
                    updateTradeOrOpenOfferManager(tradeId);
                }

                success = true;
            }
        } catch (TransactionVerificationException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;

            // We prefer to close the dispute in that case. If there was no deposit tx and a random tx was used
            // we get a TransactionVerificationException. No reason to keep that dispute open...
            updateTradeOrOpenOfferManager(tradeId);

            throw new RuntimeException(errorMessage);
        } catch (AddressFormatException | WalletException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;
            throw new RuntimeException(errorMessage);
        } finally {
            // We use the disputeCommunicationMessage as we only persist those not the disputeResultMessage.
            // If we would use the disputeResultMessage we could not lookup for the msg when we receive the AckMessage.
            chatManager.sendAckMessage(disputeCommunicationMessage, dispute.getConflictResolverPubKeyRing(), success, errorMessage);
        }
    }

    private void updateTradeOrOpenOfferManager(String tradeId) {
        // set state after payout as we call swapTradeEntryToAvailableEntry
        if (tradeManager.getTradeById(tradeId).isPresent()) {
            tradeManager.closeDisputedTrade(tradeId);
        } else {
            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(tradeId);
            openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
        }
    }

    // Losing trader or in case of 50/50 the seller gets the tx sent from the winner or buyer
    void onDisputedPayoutTxMessage(PeerPublishedDisputePayoutTxMessage peerPublishedDisputePayoutTxMessage) {
        String uid = peerPublishedDisputePayoutTxMessage.getUid();
        String tradeId = peerPublishedDisputePayoutTxMessage.getTradeId();
        Optional<Dispute> disputeOptional = findOwnDispute(tradeId);
        if (!disputeOptional.isPresent()) {
            log.debug("We got a peerPublishedPayoutTxMessage but we don't have a matching dispute. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 3 sec. to be sure the close msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputedPayoutTxMessage(peerPublishedDisputePayoutTxMessage), 3);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a peerPublishedPayoutTxMessage after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }

        Dispute dispute = disputeOptional.get();
        Contract contract = dispute.getContract();
        PubKeyRing ownPubKeyRing = keyRing.getPubKeyRing();
        boolean isBuyer = ownPubKeyRing.equals(contract.getBuyerPubKeyRing());
        PubKeyRing peersPubKeyRing = isBuyer ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();

        cleanupRetryMap(uid);
        Transaction walletTx = tradeWalletService.addTxToWallet(peerPublishedDisputePayoutTxMessage.getTransaction());
        dispute.setDisputePayoutTxId(walletTx.getHashAsString());
        BtcWalletService.printTx("Disputed payoutTx received from peer", walletTx);

        // We can only send the ack msg if we have the peersPubKeyRing which requires the dispute
        chatManager.sendAckMessage(peerPublishedDisputePayoutTxMessage, peersPubKeyRing, true, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Storage<DisputeList> getDisputeStorage() {
        return disputeStorage;
    }

    public ObservableList<Dispute> getDisputesAsObservableList() {
        if (disputes == null) {
            log.warn("disputes is null");
            return FXCollections.observableArrayList();
        }
        return disputes.getList();
    }

    public boolean isTrader(Dispute dispute) {
        return keyRing.getPubKeyRing().equals(dispute.getTraderPubKeyRing());
    }

    private boolean isArbitrator(Dispute dispute) {
        return keyRing.getPubKeyRing().equals(dispute.getConflictResolverPubKeyRing());
    }

    public String getNrOfDisputes(boolean isBuyer, Contract contract) {
        return String.valueOf(getDisputesAsObservableList().stream()
                .filter(e -> {
                    Contract contract1 = e.getContract();
                    if (contract1 == null)
                        return false;

                    if (isBuyer) {
                        NodeAddress buyerNodeAddress = contract1.getBuyerNodeAddress();
                        return buyerNodeAddress != null && buyerNodeAddress.equals(contract.getBuyerNodeAddress());
                    } else {
                        NodeAddress sellerNodeAddress = contract1.getSellerNodeAddress();
                        return sellerNodeAddress != null && sellerNodeAddress.equals(contract.getSellerNodeAddress());
                    }
                })
                .collect(Collectors.toSet()).size());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Utils
    ///////////////////////////////////////////////////////////////////////////////////////////

    Tuple2<NodeAddress, PubKeyRing> getNodeAddressPubKeyRingTuple(Dispute dispute) {
        PubKeyRing receiverPubKeyRing = null;
        NodeAddress peerNodeAddress = null;
        if (isTrader(dispute)) {
            receiverPubKeyRing = dispute.getConflictResolverPubKeyRing();
            peerNodeAddress = dispute.getConflictResolverNodeAddress();
        } else if (isArbitrator(dispute)) {
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

    private Optional<Dispute> findDispute(Dispute dispute) {
        return findDispute(dispute.getTradeId(), dispute.getTraderId(), dispute.isMediationDispute());
    }

    private Optional<Dispute> findDispute(DisputeResult disputeResult) {
        DisputeCommunicationMessage disputeCommunicationMessage = disputeResult.getDisputeCommunicationMessage();
        checkNotNull(disputeCommunicationMessage, "disputeCommunicationMessage must not be null");
        return findDispute(disputeResult.getTradeId(), disputeResult.getTraderId(), disputeCommunicationMessage.isMediationDispute());
    }

    Optional<Dispute> findDispute(DisputeCommunicationMessage message) {
        return findDispute(message.getTradeId(), message.getTraderId(), message.isMediationDispute());
    }

    private Optional<Dispute> findDispute(String tradeId, int traderId, boolean isMediationDispute) {
        if (disputes == null) {
            log.warn("disputes is null");
            return Optional.empty();
        }
        return disputes.stream()
                .filter(e -> e.getTradeId().equals(tradeId) &&
                        e.getTraderId() == traderId &&
                        e.isMediationDispute() == isMediationDispute)
                .findAny();
    }

    public Optional<Dispute> findOwnDispute(String tradeId) {
        if (disputes == null) {
            log.warn("disputes is null");
            return Optional.empty();
        }
        return disputes.stream().filter(e -> e.getTradeId().equals(tradeId)).findAny();
    }

    private void cleanupRetryMap(String uid) {
        if (delayMsgMap.containsKey(uid)) {
            Timer timer = delayMsgMap.remove(uid);
            if (timer != null)
                timer.stop();
        }
    }

}
