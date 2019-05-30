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

package bisq.core.arbitration;

import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.arbitration.messages.DisputeMessage;
import bisq.core.arbitration.messages.DisputeResultMessage;
import bisq.core.arbitration.messages.OpenNewDisputeMessage;
import bisq.core.arbitration.messages.PeerOpenedDisputeMessage;
import bisq.core.arbitration.messages.PeerPublishedDisputePayoutTxMessage;
import bisq.core.btc.exceptions.TransactionVerificationException;
import bisq.core.btc.exceptions.TxBroadcastException;
import bisq.core.btc.exceptions.WalletException;
import bisq.core.btc.setup.WalletsSetup;
import bisq.core.btc.wallet.BtcWalletService;
import bisq.core.btc.wallet.TradeWalletService;
import bisq.core.btc.wallet.TxBroadcaster;
import bisq.core.locale.Res;
import bisq.core.offer.OpenOffer;
import bisq.core.offer.OpenOfferManager;
import bisq.core.trade.Contract;
import bisq.core.trade.Tradable;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;
import bisq.core.trade.closed.ClosedTradableManager;

import bisq.network.p2p.AckMessage;
import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.BootstrapListener;
import bisq.network.p2p.DecryptedMessageWithPubKey;
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
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.proto.persistable.PersistenceProtoResolver;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;

import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;

import com.google.inject.Inject;

import javax.inject.Named;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;

import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleIntegerProperty;

import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;

import java.io.File;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

import javax.annotation.Nullable;

public class DisputeManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(DisputeManager.class);

    private final TradeWalletService tradeWalletService;
    private final BtcWalletService walletService;
    private final WalletsSetup walletsSetup;
    private final TradeManager tradeManager;
    private final ClosedTradableManager closedTradableManager;
    private final OpenOfferManager openOfferManager;
    private final P2PService p2PService;
    private final KeyRing keyRing;
    private final Storage<DisputeList> disputeStorage;
    private DisputeList disputes;
    private final String disputeInfo;
    private final CopyOnWriteArraySet<DecryptedMessageWithPubKey> decryptedMailboxMessageWithPubKeys = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<DecryptedMessageWithPubKey> decryptedDirectMessageWithPubKeys = new CopyOnWriteArraySet<>();
    private final Map<String, Dispute> openDisputes;
    private final Map<String, Dispute> closedDisputes;
    private final Map<String, Timer> delayMsgMap = new HashMap<>();

    private final Map<String, Subscription> disputeIsClosedSubscriptionsMap = new HashMap<>();
    @Getter
    private final IntegerProperty numOpenDisputes = new SimpleIntegerProperty();


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
                          PersistenceProtoResolver persistenceProtoResolver,
                          @Named(Storage.STORAGE_DIR) File storageDir) {
        this.p2PService = p2PService;
        this.tradeWalletService = tradeWalletService;
        this.walletService = walletService;
        this.walletsSetup = walletsSetup;
        this.tradeManager = tradeManager;
        this.closedTradableManager = closedTradableManager;
        this.openOfferManager = openOfferManager;
        this.keyRing = keyRing;

        disputeStorage = new Storage<>(storageDir, persistenceProtoResolver);

        openDisputes = new HashMap<>();
        closedDisputes = new HashMap<>();

        disputeInfo = Res.get("support.initialInfo");

        // We get first the message handler called then the onBootstrapped
        p2PService.addDecryptedDirectMessageListener((decryptedMessageWithPubKey, senderAddress) -> {
            decryptedDirectMessageWithPubKeys.add(decryptedMessageWithPubKey);
            tryApplyMessages();
        });
        p2PService.addDecryptedMailboxListener((decryptedMessageWithPubKey, senderAddress) -> {
            decryptedMailboxMessageWithPubKeys.add(decryptedMessageWithPubKey);
            tryApplyMessages();
        });
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

        disputes.getList().addListener((ListChangeListener<Dispute>) change -> {
            change.next();
            onDisputesChangeListener(change.getAddedSubList(), change.getRemoved());
        });
        onDisputesChangeListener(disputes.getList(), null);
    }

    private void onDisputesChangeListener(List<? extends Dispute> addedList, @Nullable List<? extends Dispute> removedList) {
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
                        // We get the event before the list gets updated, so we execute on next frame
                        UserThread.execute(() -> {
                            int openDisputes = disputes.getList().stream()
                                    .filter(e -> !e.isClosed())
                                    .collect(Collectors.toList()).size();
                            numOpenDisputes.set(openDisputes);
                        });
                    });
            disputeIsClosedSubscriptionsMap.put(id, disputeStateSubscription);
        });
    }

    public void cleanupDisputes() {
        disputes.stream().forEach(dispute -> {
            dispute.setStorage(disputeStorage);
            if (dispute.isClosed())
                closedDisputes.put(dispute.getTradeId(), dispute);
            else
                openDisputes.put(dispute.getTradeId(), dispute);
        });

        // If we have duplicate disputes we close the second one (might happen if both traders opened a dispute and arbitrator
        // was offline, so could not forward msg to other peer, then the arbitrator might have 4 disputes open for 1 trade)
        openDisputes.forEach((key, openDispute) -> {
            if (closedDisputes.containsKey(key)) {
                final Dispute closedDispute = closedDisputes.get(key);
                // We need to check if is from the same peer, we don't want to close the peers dispute
                if (closedDispute.getTraderId() == openDispute.getTraderId()) {
                    openDispute.setIsClosed(true);
                    tradeManager.closeDisputedTrade(openDispute.getTradeId());
                }
            }
        });
    }

    private void tryApplyMessages() {
        if (isReadyForTxBroadcast())
            applyMessages();
    }

    private boolean isReadyForTxBroadcast() {
        return p2PService.isBootstrapped() &&
                walletsSetup.isDownloadComplete() &&
                walletsSetup.hasSufficientPeersForBroadcast();
    }

    private void applyMessages() {
        decryptedDirectMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            if (networkEnvelope instanceof DisputeMessage) {
                dispatchMessage((DisputeMessage) networkEnvelope);
            } else if (networkEnvelope instanceof AckMessage) {
                processAckMessage((AckMessage) networkEnvelope, null);
            }
        });
        decryptedDirectMessageWithPubKeys.clear();

        decryptedMailboxMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            log.debug("decryptedMessageWithPubKey.message " + networkEnvelope);
            if (networkEnvelope instanceof DisputeMessage) {
                dispatchMessage((DisputeMessage) networkEnvelope);
                p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
            } else if (networkEnvelope instanceof AckMessage) {
                processAckMessage((AckMessage) networkEnvelope, decryptedMessageWithPubKey);
            }
        });
        decryptedMailboxMessageWithPubKeys.clear();
    }

    private void processAckMessage(AckMessage ackMessage, @Nullable DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        if (ackMessage.getSourceType() == AckMessageSourceType.DISPUTE_MESSAGE) {
            if (ackMessage.isSuccess()) {
                log.info("Received AckMessage for {} with tradeId {} and uid {}",
                        ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getSourceUid());
            } else {
                log.warn("Received AckMessage with error state for {} with tradeId {} and errorMessage={}",
                        ackMessage.getSourceMsgClassName(), ackMessage.getSourceId(), ackMessage.getErrorMessage());
            }

            disputes.getList().stream()
                    .flatMap(dispute -> dispute.getDisputeCommunicationMessages().stream())
                    .filter(msg -> msg.getUid().equals(ackMessage.getSourceUid()))
                    .forEach(msg -> {
                        if (ackMessage.isSuccess())
                            msg.setAcknowledged(true);
                        else
                            msg.setAckError(ackMessage.getErrorMessage());
                    });
            disputes.persist();

            if (decryptedMessageWithPubKey != null)
                p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
        }
    }

    private void dispatchMessage(DisputeMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());

        if (message instanceof OpenNewDisputeMessage)
            onOpenNewDisputeMessage((OpenNewDisputeMessage) message);
        else if (message instanceof PeerOpenedDisputeMessage)
            onPeerOpenedDisputeMessage((PeerOpenedDisputeMessage) message);
        else if (message instanceof DisputeCommunicationMessage)
            onDisputeDirectMessage((DisputeCommunicationMessage) message);
        else if (message instanceof DisputeResultMessage)
            onDisputeResultMessage((DisputeResultMessage) message);
        else if (message instanceof PeerPublishedDisputePayoutTxMessage)
            onDisputedPayoutTxMessage((PeerPublishedDisputePayoutTxMessage) message);
        else
            log.warn("Unsupported message at dispatchMessage.\nmessage=" + message);
    }

    public void sendOpenNewDisputeMessage(Dispute dispute, boolean reOpen, ResultHandler resultHandler, FaultHandler faultHandler) {
        if (!disputes.contains(dispute)) {
            final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
            if (!storedDisputeOptional.isPresent() || reOpen) {
                String sysMsg = dispute.isSupportTicket() ?
                        Res.get("support.youOpenedTicket", disputeInfo, Version.VERSION)
                        : Res.get("support.youOpenedDispute", disputeInfo, Version.VERSION);

                DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                        dispute.getTradeId(),
                        keyRing.getPubKeyRing().hashCode(),
                        false,
                        Res.get("support.systemMsg", sysMsg),
                        p2PService.getAddress()
                );
                disputeCommunicationMessage.setSystemMessage(true);
                dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
                if (!reOpen) {
                    disputes.add(dispute);
                }

                NodeAddress peersNodeAddress = dispute.getContract().getArbitratorNodeAddress();
                OpenNewDisputeMessage openNewDisputeMessage = new OpenNewDisputeMessage(dispute, p2PService.getAddress(),
                        UUID.randomUUID().toString());
                log.info("Send {} to peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                "disputeCommunicationMessage.uid={}",
                        openNewDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                        openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                        disputeCommunicationMessage.getUid());
                p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
                        dispute.getArbitratorPubKeyRing(),
                        openNewDisputeMessage,
                        new SendMailboxMessageListener() {
                            @Override
                            public void onArrived() {
                                log.info("{} arrived at peer {}. tradeId={}, openNewDisputeMessage.uid={}, " +
                                                "disputeCommunicationMessage.uid={}",
                                        openNewDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
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
                                        openNewDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
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
                                        openNewDisputeMessage.getClass().getSimpleName(), peersNodeAddress,
                                        openNewDisputeMessage.getTradeId(), openNewDisputeMessage.getUid(),
                                        disputeCommunicationMessage.getUid(), errorMessage);

                                // We use the disputeCommunicationMessage wrapped inside the openNewDisputeMessage for
                                // the state, as that is displayed to the user and we only persist that msg
                                disputeCommunicationMessage.setSendMessageError(errorMessage);
                                disputes.persist();
                                faultHandler.handleFault("Sending dispute message failed: " +
                                        errorMessage, new MessageDeliveryFailedException());
                            }
                        }
                );
            } else {
                final String msg = "We got a dispute already open for that trade and trading peer.\n" +
                        "TradeId = " + dispute.getTradeId();
                log.warn(msg);
                faultHandler.handleFault(msg, new DisputeAlreadyOpenException());
            }
        } else {
            final String msg = "We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId();
            log.warn(msg);
            faultHandler.handleFault(msg, new DisputeAlreadyOpenException());
        }
    }

    // arbitrator sends that to trading peer when he received openDispute request
    private String sendPeerOpenedDisputeMessage(Dispute disputeFromOpener, Contract contractFromOpener, PubKeyRing pubKeyRing) {
        Dispute dispute = new Dispute(
                disputeStorage,
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
                disputeFromOpener.getArbitratorPubKeyRing(),
                disputeFromOpener.isSupportTicket()
        );
        final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
        if (!storedDisputeOptional.isPresent()) {
            String sysMsg = dispute.isSupportTicket() ?
                    Res.get("support.peerOpenedTicket", disputeInfo)
                    : Res.get("support.peerOpenedDispute", disputeInfo);
            DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                    dispute.getTradeId(),
                    keyRing.getPubKeyRing().hashCode(),
                    false,
                    Res.get("support.systemMsg", sysMsg),
                    p2PService.getAddress()
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

    // traders send msg to the arbitrator or arbitrator to 1 trader (trader to trader is not allowed)
    public DisputeCommunicationMessage sendDisputeDirectMessage(Dispute dispute, String text, ArrayList<Attachment> attachments) {
        DisputeCommunicationMessage message = new DisputeCommunicationMessage(
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                isTrader(dispute),
                text,
                p2PService.getAddress()
        );

        message.addAllAttachments(attachments);
        Tuple2<NodeAddress, PubKeyRing> tuple = getNodeAddressPubKeyRingTuple(dispute);
        NodeAddress peersNodeAddress = tuple.first;
        PubKeyRing receiverPubKeyRing = tuple.second;

        if (isTrader(dispute) ||
                (isArbitrator(dispute) && !message.isSystemMessage()))
            dispute.addDisputeCommunicationMessage(message);

        if (receiverPubKeyRing != null) {
            log.info("Send {} to peer {}. tradeId={}, uid={}",
                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());

            p2PService.sendEncryptedMailboxMessage(peersNodeAddress,
                    receiverPubKeyRing,
                    message,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("{} arrived at peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            message.setArrived(true);
                            disputes.persist();
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("{} stored in mailbox for peer {}. tradeId={}, uid={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid());
                            message.setStoredInMailbox(true);
                            disputes.persist();
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("{} failed: Peer {}. tradeId={}, uid={}, errorMessage={}",
                                    message.getClass().getSimpleName(), peersNodeAddress, message.getTradeId(), message.getUid(), errorMessage);
                            message.setSendMessageError(errorMessage);
                            disputes.persist();
                        }
                    }
            );
        }

        return message;
    }

    // arbitrator send result to trader
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String text) {
        DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                false,
                text,
                p2PService.getAddress()
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
        final PeerPublishedDisputePayoutTxMessage message = new PeerPublishedDisputePayoutTxMessage(transaction.bitcoinSerialize(),
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

    private void sendAckMessage(DisputeMessage disputeMessage, PubKeyRing peersPubKeyRing,
                                boolean result, @Nullable String errorMessage) {
        String tradeId = disputeMessage.getTradeId();
        String uid = disputeMessage.getUid();
        AckMessage ackMessage = new AckMessage(p2PService.getNetworkNode().getNodeAddress(),
                AckMessageSourceType.DISPUTE_MESSAGE,
                disputeMessage.getClass().getSimpleName(),
                uid,
                tradeId,
                result,
                errorMessage);
        final NodeAddress peersNodeAddress = disputeMessage.getSenderNodeAddress();
        log.info("Send AckMessage for {} to peer {}. tradeId={}, uid={}",
                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
        p2PService.sendEncryptedMailboxMessage(
                peersNodeAddress,
                peersPubKeyRing,
                ackMessage,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("AckMessage for {} arrived at peer {}. tradeId={}, uid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("AckMessage for {} stored in mailbox for peer {}. tradeId={}, uid={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("AckMessage for {} failed. Peer {}. tradeId={}, uid={}, errorMessage={}",
                                ackMessage.getSourceMsgClassName(), peersNodeAddress, tradeId, uid, errorMessage);
                    }
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    // arbitrator receives that from trader who opens dispute
    private void onOpenNewDisputeMessage(OpenNewDisputeMessage openNewDisputeMessage) {
        String errorMessage;
        Dispute dispute = openNewDisputeMessage.getDispute();
        Contract contractFromOpener = dispute.getContract();
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contractFromOpener.getSellerPubKeyRing() : contractFromOpener.getBuyerPubKeyRing();
        if (isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
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
            sendAckMessage(msg, sendersPubKeyRing, errorMessage == null, errorMessage);
        }
    }

    // not dispute requester receives that from arbitrator
    private void onPeerOpenedDisputeMessage(PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        String errorMessage;
        Dispute dispute = peerOpenedDisputeMessage.getDispute();
        if (!isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
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
            sendAckMessage(msg, dispute.getArbitratorPubKeyRing(), errorMessage == null, errorMessage);
        }

        sendAckMessage(peerOpenedDisputeMessage, dispute.getArbitratorPubKeyRing(), errorMessage == null, errorMessage);
    }

    // A trader can receive a msg from the arbitrator or the arbitrator from a trader. Trader to trader is not allowed.
    private void onDisputeDirectMessage(DisputeCommunicationMessage disputeCommunicationMessage) {
        final String tradeId = disputeCommunicationMessage.getTradeId();
        final String uid = disputeCommunicationMessage.getUid();
        Optional<Dispute> disputeOptional = findDispute(tradeId, disputeCommunicationMessage.getTraderId());
        if (!disputeOptional.isPresent()) {
            log.debug("We got a disputeCommunicationMessage but we don't have a matching dispute. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                Timer timer = UserThread.runAfter(() -> onDisputeDirectMessage(disputeCommunicationMessage), 1);
                delayMsgMap.put(uid, timer);
            } else {
                String msg = "We got a disputeCommunicationMessage after we already repeated to apply the message after a delay. That should never happen. TradeId = " + tradeId;
                log.warn(msg);
            }
            return;
        }

        cleanupRetryMap(uid);
        Dispute dispute = disputeOptional.get();
        Tuple2<NodeAddress, PubKeyRing> tuple = getNodeAddressPubKeyRingTuple(dispute);
        PubKeyRing receiverPubKeyRing = tuple.second;

        if (!dispute.getDisputeCommunicationMessages().contains(disputeCommunicationMessage))
            dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
        else
            log.warn("We got a disputeCommunicationMessage what we have already stored. TradeId = " + tradeId);

        // We never get a errorMessage in that method (only if we cannot resolve the receiverPubKeyRing but then we
        // cannot send it anyway)
        if (receiverPubKeyRing != null)
            sendAckMessage(disputeCommunicationMessage, receiverPubKeyRing, true, null);
    }

    // We get that message at both peers. The dispute object is in context of the trader
    private void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        String errorMessage = null;
        boolean success = false;
        PubKeyRing arbitratorsPubKeyRing = null;
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();

        if (isArbitrator(disputeResult)) {
            log.error("Arbitrator received disputeResultMessage. That must never happen.");
            return;
        }

        final String tradeId = disputeResult.getTradeId();
        Optional<Dispute> disputeOptional = findDispute(tradeId, disputeResult.getTraderId());
        final String uid = disputeResultMessage.getUid();
        if (!disputeOptional.isPresent()) {
            log.debug("We got a dispute result msg but we don't have a matching dispute. " +
                    "That might happen when we get the disputeResultMessage before the dispute was created. " +
                    "We try again after 2 sec. to apply the disputeResultMessage. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay2 sec. to be sure the comm. msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputeResultMessage(disputeResultMessage), 2);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a dispute result msg after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
            return;
        }
        Dispute dispute = disputeOptional.get();
        try {
            cleanupRetryMap(uid);
            arbitratorsPubKeyRing = dispute.getArbitratorPubKeyRing();
            DisputeCommunicationMessage disputeCommunicationMessage = disputeResult.getDisputeCommunicationMessage();
            if (!dispute.getDisputeCommunicationMessages().contains(disputeCommunicationMessage))
                dispute.addDisputeCommunicationMessage(disputeCommunicationMessage);
            else if (disputeCommunicationMessage != null)
                log.warn("We got a dispute mail msg what we have already stored. TradeId = " + disputeCommunicationMessage.getTradeId());

            dispute.setIsClosed(true);

            if (dispute.disputeResultProperty().get() != null)
                log.warn("We got already a dispute result. That should only happen if a dispute needs to be closed " +
                        "again because the first close did not succeed. TradeId = " + tradeId);

            dispute.setDisputeResult(disputeResult);

            // We need to avoid publishing the tx from both traders as it would create problems with zero confirmation withdrawals
            // There would be different transactions if both sign and publish (signers: once buyer+arb, once seller+arb)
            // The tx publisher is the winner or in case both get 50% the buyer, as the buyer has more inventive to publish the tx as he receives
            // more BTC as he has deposited
            final Contract contract = dispute.getContract();

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

                final Optional<Trade> tradeOptional = tradeManager.getTradeById(tradeId);
                Transaction payoutTx = null;
                if (tradeOptional.isPresent()) {
                    payoutTx = tradeOptional.get().getPayoutTx();
                } else {
                    final Optional<Tradable> tradableOptional = closedTradableManager.getTradableById(tradeId);
                    if (tradableOptional.isPresent() && tradableOptional.get() instanceof Trade) {
                        payoutTx = ((Trade) tradableOptional.get()).getPayoutTx();
                    }
                }

                if (payoutTx == null) {
                    if (dispute.getDepositTxSerialized() != null) {
                        byte[] multiSigPubKey = isBuyer ? contract.getBuyerMultiSigPubKey() : contract.getSellerMultiSigPubKey();
                        DeterministicKey multiSigKeyPair = walletService.getMultiSigKeyPair(dispute.getTradeId(), multiSigPubKey);
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

                                // set state after payout as we call swapTradeEntryToAvailableEntry
                                if (tradeManager.getTradeById(dispute.getTradeId()).isPresent())
                                    tradeManager.closeDisputedTrade(dispute.getTradeId());
                                else {
                                    Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(dispute.getTradeId());
                                    openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
                                }
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
                if (dispute.disputeResultProperty().get() != null &&
                        dispute.isClosed() &&
                        tradeManager.getTradeById(dispute.getTradeId()).isPresent()) {
                    tradeManager.closeDisputedTrade(dispute.getTradeId());
                }

                success = true;
            }
        } catch (TransactionVerificationException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;

            // We prefer to close the dispute in that case. If there was no deposit tx and a random tx was used
            // we get a TransactionVerificationException. No reason to keep that dispute open...
            if (tradeManager.getTradeById(dispute.getTradeId()).isPresent())
                tradeManager.closeDisputedTrade(dispute.getTradeId());
            else {
                Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(dispute.getTradeId());
                openOfferOptional.ifPresent(openOffer -> openOfferManager.closeOpenOffer(openOffer.getOffer()));
            }
            dispute.setIsClosed(true);

            throw new RuntimeException(errorMessage);
        } catch (AddressFormatException | WalletException e) {
            errorMessage = "Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString();
            log.error(errorMessage, e);
            success = false;
            throw new RuntimeException(errorMessage);
        } finally {
            if (arbitratorsPubKeyRing != null) {
                // We use the disputeCommunicationMessage as we only persist those not the disputeResultMessage.
                // If we would use the disputeResultMessage we could not lookup for the msg when we receive the AckMessage.
                DisputeCommunicationMessage disputeCommunicationMessage = disputeResultMessage.getDisputeResult().getDisputeCommunicationMessage();
                sendAckMessage(disputeCommunicationMessage, arbitratorsPubKeyRing, success, errorMessage);
            }
        }
    }

    // Losing trader or in case of 50/50 the seller gets the tx sent from the winner or buyer
    private void onDisputedPayoutTxMessage(PeerPublishedDisputePayoutTxMessage peerPublishedDisputePayoutTxMessage) {
        final String uid = peerPublishedDisputePayoutTxMessage.getUid();
        final String tradeId = peerPublishedDisputePayoutTxMessage.getTradeId();
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
        final Contract contract = dispute.getContract();
        PubKeyRing ownPubKeyRing = keyRing.getPubKeyRing();
        boolean isBuyer = ownPubKeyRing.equals(contract.getBuyerPubKeyRing());
        PubKeyRing peersPubKeyRing = isBuyer ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();

        cleanupRetryMap(uid);
        Transaction walletTx = tradeWalletService.addTxToWallet(peerPublishedDisputePayoutTxMessage.getTransaction());
        dispute.setDisputePayoutTxId(walletTx.getHashAsString());
        BtcWalletService.printTx("Disputed payoutTx received from peer", walletTx);

        // We can only send the ack msg if we have the peersPubKeyRing which requires the dispute
        sendAckMessage(peerPublishedDisputePayoutTxMessage, peersPubKeyRing, true, null);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Storage<DisputeList> getDisputeStorage() {
        return disputeStorage;
    }

    public ObservableList<Dispute> getDisputesAsObservableList() {
        return disputes.getList();
    }

    public boolean isTrader(Dispute dispute) {
        return keyRing.getPubKeyRing().equals(dispute.getTraderPubKeyRing());
    }

    private boolean isArbitrator(Dispute dispute) {
        return keyRing.getPubKeyRing().equals(dispute.getArbitratorPubKeyRing());
    }

    private boolean isArbitrator(DisputeResult disputeResult) {
        return Arrays.equals(disputeResult.getArbitratorPubKey(),
                walletService.getArbitratorAddressEntry().getPubKey());
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


    private Tuple2<NodeAddress, PubKeyRing> getNodeAddressPubKeyRingTuple(Dispute dispute) {
        PubKeyRing receiverPubKeyRing = null;
        NodeAddress peerNodeAddress = null;
        if (isTrader(dispute)) {
            receiverPubKeyRing = dispute.getArbitratorPubKeyRing();
            peerNodeAddress = dispute.getContract().getArbitratorNodeAddress();
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

    private Optional<Dispute> findDispute(String tradeId, int traderId) {
        return disputes.stream().filter(e -> e.getTradeId().equals(tradeId) && e.getTraderId() == traderId).findAny();
    }

    public Optional<Dispute> findOwnDispute(String tradeId) {
        return getDisputeStream(tradeId).findAny();
    }

    private Stream<Dispute> getDisputeStream(String tradeId) {
        return disputes.stream().filter(e -> e.getTradeId().equals(tradeId));
    }

    private void cleanupRetryMap(String uid) {
        if (delayMsgMap.containsKey(uid)) {
            Timer timer = delayMsgMap.remove(uid);
            if (timer != null)
                timer.stop();
        }
    }

}
