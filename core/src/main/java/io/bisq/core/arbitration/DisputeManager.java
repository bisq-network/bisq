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

package io.bisq.core.arbitration;

import com.google.common.util.concurrent.FutureCallback;
import com.google.inject.Inject;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.handlers.FaultHandler;
import io.bisq.common.handlers.ResultHandler;
import io.bisq.common.locale.Res;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.Storage;
import io.bisq.core.arbitration.messages.*;
import io.bisq.core.btc.AddressEntry;
import io.bisq.core.btc.exceptions.TransactionVerificationException;
import io.bisq.core.btc.exceptions.WalletException;
import io.bisq.core.btc.wallet.BtcWalletService;
import io.bisq.core.btc.wallet.TradeWalletService;
import io.bisq.core.offer.OpenOffer;
import io.bisq.core.offer.OpenOfferManager;
import io.bisq.core.trade.Contract;
import io.bisq.core.trade.Tradable;
import io.bisq.core.trade.Trade;
import io.bisq.core.trade.TradeManager;
import io.bisq.core.trade.closed.ClosedTradableManager;
import io.bisq.network.p2p.*;
import javafx.collections.ObservableList;
import org.bitcoinj.core.AddressFormatException;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.crypto.DeterministicKey;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Named;
import java.io.File;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DisputeManager implements PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(DisputeManager.class);

    private final TradeWalletService tradeWalletService;
    private final BtcWalletService walletService;
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public DisputeManager(P2PService p2PService,
                          TradeWalletService tradeWalletService,
                          BtcWalletService walletService,
                          TradeManager tradeManager,
                          ClosedTradableManager closedTradableManager,
                          OpenOfferManager openOfferManager,
                          KeyRing keyRing,
                          PersistenceProtoResolver persistenceProtoResolver,
                          @Named(Storage.STORAGE_DIR) File storageDir) {
        this.p2PService = p2PService;
        this.tradeWalletService = tradeWalletService;
        this.walletService = walletService;
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
            if (p2PService.isBootstrapped())
                applyMessages();
        });
        p2PService.addDecryptedMailboxListener((decryptedMessageWithPubKey, senderAddress) -> {
            decryptedMailboxMessageWithPubKeys.add(decryptedMessageWithPubKey);
            if (p2PService.isBootstrapped())
                applyMessages();
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted() {
        disputes = new DisputeList(disputeStorage);
        disputes.readPersisted();
        disputes.stream().forEach(dispute -> dispute.setStorage(getDisputeStorage()));
    }

    public void onAllServicesInitialized() {
        if (p2PService.isBootstrapped())
            applyMessages();
        else
            p2PService.addP2PServiceListener(new BootstrapListener() {
                @Override
                public void onBootstrapComplete() {
                    applyMessages();
                }
            });

        cleanupDisputes();
    }

    public void cleanupDisputes() {
        disputes.stream().forEach(dispute -> {
            dispute.setStorage(getDisputeStorage());
            if (dispute.isClosed())
                closedDisputes.put(dispute.getTradeId(), dispute);
            else
                openDisputes.put(dispute.getTradeId(), dispute);
        });

        // If we have duplicate disputes we close the second one (might happen if both traders opened a dispute and arbitrator
        // was offline, so could not forward msg to other peer, then the arbitrator might have 4 disputes open for 1 trade)
        openDisputes.entrySet().stream().forEach(openDisputeEntry -> {
            String key = openDisputeEntry.getKey();
            if (closedDisputes.containsKey(key)) {
                final Dispute closedDispute = closedDisputes.get(key);
                final Dispute openDispute = openDisputeEntry.getValue();
                // We need to check if is from the same peer, we don't want to close the peers dispute
                if (closedDispute.getTraderId() == openDispute.getTraderId()) {
                    openDispute.setIsClosed(true);
                    tradeManager.closeDisputedTrade(openDispute.getTradeId());
                }
            }
        });
    }

    private void applyMessages() {
        decryptedDirectMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            log.debug("decryptedDirectMessageWithPubKeys.message " + networkEnvelope);
            if (networkEnvelope instanceof DisputeMessage)
                dispatchMessage((DisputeMessage) networkEnvelope);
        });
        decryptedDirectMessageWithPubKeys.clear();

        decryptedMailboxMessageWithPubKeys.forEach(decryptedMessageWithPubKey -> {
            NetworkEnvelope networkEnvelope = decryptedMessageWithPubKey.getNetworkEnvelope();
            log.debug("decryptedMessageWithPubKey.message " + networkEnvelope);
            if (networkEnvelope instanceof DisputeMessage) {
                dispatchMessage((DisputeMessage) networkEnvelope);
                p2PService.removeEntryFromMailbox(decryptedMessageWithPubKey);
            }
        });
        decryptedMailboxMessageWithPubKeys.clear();
    }

    private void dispatchMessage(DisputeMessage message) {
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
                        Res.get("support.youOpenedTicket")
                        : Res.get("support.youOpenedDispute", disputeInfo);

                DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                        dispute.getTradeId(),
                        keyRing.getPubKeyRing().hashCode(),
                        false,
                        Res.get("support.systemMsg", sysMsg),
                        null,
                        p2PService.getAddress(),
                        new Date().getTime(),
                        false,
                        false,
                        UUID.randomUUID().toString()
                );
                disputeCommunicationMessage.setSystemMessage(true);
                dispute.addDisputeMessage(disputeCommunicationMessage);
                if (!reOpen) {
                    disputes.add(dispute);
                }

                p2PService.sendEncryptedMailboxMessage(dispute.getContract().getArbitratorNodeAddress(),
                        dispute.getArbitratorPubKeyRing(),
                        new OpenNewDisputeMessage(dispute, p2PService.getAddress(),
                                UUID.randomUUID().toString()),
                        new SendMailboxMessageListener() {
                            @Override
                            public void onArrived() {
                                log.info("Message arrived at peer. tradeId={}", disputeCommunicationMessage.getTradeId());
                                disputeCommunicationMessage.setArrived(true);
                                resultHandler.handleResult();
                            }

                            @Override
                            public void onStoredInMailbox() {
                                log.info("Message stored in mailbox. tradeId={}", disputeCommunicationMessage.getTradeId());
                                disputeCommunicationMessage.setStoredInMailbox(true);
                                resultHandler.handleResult();
                            }

                            @Override
                            public void onFault(String errorMessage) {
                                log.error("sendEncryptedMailboxMessage failed. disputeCommunicationMessage=" + disputeCommunicationMessage);
                                faultHandler.handleFault("Sending dispute message failed: " + errorMessage, new MessageDeliveryFailedException());
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
    private void sendPeerOpenedDisputeMessage(Dispute disputeFromOpener) {
        Contract contractFromOpener = disputeFromOpener.getContract();
        PubKeyRing pubKeyRing = disputeFromOpener.isDisputeOpenerIsBuyer() ? contractFromOpener.getSellerPubKeyRing() : contractFromOpener.getBuyerPubKeyRing();
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
                    Res.get("support.peerOpenedTicket")
                    : Res.get("support.peerOpenedDispute", disputeInfo);
            DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                    dispute.getTradeId(),
                    keyRing.getPubKeyRing().hashCode(),
                    false,
                    Res.get("support.systemMsg", sysMsg),
                    null,
                    p2PService.getAddress(),
                    new Date().getTime(),
                    false,
                    false,
                    UUID.randomUUID().toString()
            );
            disputeCommunicationMessage.setSystemMessage(true);
            dispute.addDisputeMessage(disputeCommunicationMessage);
            disputes.add(dispute);

            // we mirrored dispute already!
            Contract contract = dispute.getContract();
            PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerPubKeyRing() : contract.getSellerPubKeyRing();
            NodeAddress peerNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getBuyerNodeAddress() : contract.getSellerNodeAddress();
            log.trace("sendPeerOpenedDisputeMessage to peerAddress " + peerNodeAddress);
            p2PService.sendEncryptedMailboxMessage(peerNodeAddress,
                    peersPubKeyRing,
                    new PeerOpenedDisputeMessage(dispute,
                            p2PService.getAddress(),
                            UUID.randomUUID().toString()),
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}", disputeCommunicationMessage.getTradeId());
                            disputeCommunicationMessage.setArrived(true);
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox. tradeId={}", disputeCommunicationMessage.getTradeId());
                            disputeCommunicationMessage.setStoredInMailbox(true);
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("sendEncryptedMailboxMessage failed. disputeCommunicationMessage=" + disputeCommunicationMessage);
                        }
                    }
            );
        } else {
            log.warn("We got a dispute already open for that trade and trading peer.\n" +
                    "TradeId = " + dispute.getTradeId());
        }
    }

    // traders send msg to the arbitrator or arbitrator to 1 trader (trader to trader is not allowed)
    public DisputeCommunicationMessage sendDisputeDirectMessage(Dispute dispute, String text, ArrayList<Attachment> attachments) {
        DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                isTrader(dispute),
                text,
                null,
                p2PService.getAddress(),
                new Date().getTime(),
                false,
                false,
                UUID.randomUUID().toString()
        );

        disputeCommunicationMessage.addAllAttachments(attachments);
        PubKeyRing receiverPubKeyRing = null;
        NodeAddress peerNodeAddress = null;
        if (isTrader(dispute)) {
            dispute.addDisputeMessage(disputeCommunicationMessage);
            receiverPubKeyRing = dispute.getArbitratorPubKeyRing();
            peerNodeAddress = dispute.getContract().getArbitratorNodeAddress();
        } else if (isArbitrator(dispute)) {
            if (!disputeCommunicationMessage.isSystemMessage())
                dispute.addDisputeMessage(disputeCommunicationMessage);
            receiverPubKeyRing = dispute.getTraderPubKeyRing();
            Contract contract = dispute.getContract();
            if (contract.getBuyerPubKeyRing().equals(receiverPubKeyRing))
                peerNodeAddress = contract.getBuyerNodeAddress();
            else
                peerNodeAddress = contract.getSellerNodeAddress();
        } else {
            log.error("That must not happen. Trader cannot communicate to other trader.");
        }
        if (receiverPubKeyRing != null) {
            log.trace("sendDisputeDirectMessage to peerAddress " + peerNodeAddress);
            p2PService.sendEncryptedMailboxMessage(peerNodeAddress,
                    receiverPubKeyRing,
                    disputeCommunicationMessage,
                    new SendMailboxMessageListener() {
                        @Override
                        public void onArrived() {
                            log.info("Message arrived at peer. tradeId={}", disputeCommunicationMessage.getTradeId());
                            disputeCommunicationMessage.setArrived(true);
                        }

                        @Override
                        public void onStoredInMailbox() {
                            log.info("Message stored in mailbox. tradeId={}", disputeCommunicationMessage.getTradeId());
                            disputeCommunicationMessage.setStoredInMailbox(true);
                        }

                        @Override
                        public void onFault(String errorMessage) {
                            log.error("sendEncryptedMailboxMessage failed. disputeCommunicationMessage=" + disputeCommunicationMessage);
                        }
                    }
            );
        }

        return disputeCommunicationMessage;
    }

    // arbitrator send result to trader
    public void sendDisputeResultMessage(DisputeResult disputeResult, Dispute dispute, String text) {
        DisputeCommunicationMessage disputeCommunicationMessage = new DisputeCommunicationMessage(
                dispute.getTradeId(),
                dispute.getTraderPubKeyRing().hashCode(),
                false,
                text,
                null,
                p2PService.getAddress(),
                new Date().getTime(),
                false,
                false,
                UUID.randomUUID().toString()
        );

        dispute.addDisputeMessage(disputeCommunicationMessage);
        disputeResult.setDisputeCommunicationMessage(disputeCommunicationMessage);

        NodeAddress peerNodeAddress;
        Contract contract = dispute.getContract();
        if (contract.getBuyerPubKeyRing().equals(dispute.getTraderPubKeyRing()))
            peerNodeAddress = contract.getBuyerNodeAddress();
        else
            peerNodeAddress = contract.getSellerNodeAddress();
        p2PService.sendEncryptedMailboxMessage(peerNodeAddress,
                dispute.getTraderPubKeyRing(),
                new DisputeResultMessage(disputeResult, p2PService.getAddress(),
                        UUID.randomUUID().toString()),
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("Message arrived at peer. tradeId={}", disputeCommunicationMessage.getTradeId());
                        disputeCommunicationMessage.setArrived(true);
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("Message stored in mailbox. tradeId={}", disputeCommunicationMessage.getTradeId());
                        disputeCommunicationMessage.setStoredInMailbox(true);
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("sendEncryptedMailboxMessage failed. disputeCommunicationMessage=" + disputeCommunicationMessage);
                    }
                }
        );
    }

    // winner (or buyer in case of 50/50) sends tx to other peer
    private void sendPeerPublishedPayoutTxMessage(Transaction transaction, Dispute dispute, Contract contract) {
        PubKeyRing peersPubKeyRing = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerPubKeyRing() : contract.getBuyerPubKeyRing();
        NodeAddress peerNodeAddress = dispute.isDisputeOpenerIsBuyer() ? contract.getSellerNodeAddress() : contract.getBuyerNodeAddress();
        log.trace("sendPeerPublishedPayoutTxMessage to peerAddress " + peerNodeAddress);
        final PeerPublishedDisputePayoutTxMessage message = new PeerPublishedDisputePayoutTxMessage(transaction.bitcoinSerialize(),
                dispute.getTradeId(),
                p2PService.getAddress(),
                UUID.randomUUID().toString());
        p2PService.sendEncryptedMailboxMessage(peerNodeAddress,
                peersPubKeyRing,
                message,
                new SendMailboxMessageListener() {
                    @Override
                    public void onArrived() {
                        log.info("Message arrived at peer. tradeId={}", message.getTradeId());
                    }

                    @Override
                    public void onStoredInMailbox() {
                        log.info("Message stored in mailbox. tradeId={}", message.getTradeId());
                    }

                    @Override
                    public void onFault(String errorMessage) {
                        log.error("sendEncryptedMailboxMessage failed. message=" + message);
                    }
                }
        );
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Incoming message
    ///////////////////////////////////////////////////////////////////////////////////////////

    // arbitrator receives that from trader who opens dispute
    private void onOpenNewDisputeMessage(OpenNewDisputeMessage openNewDisputeMessage) {
        Dispute dispute = openNewDisputeMessage.getDispute();
        if (isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
                if (!storedDisputeOptional.isPresent()) {
                    dispute.setStorage(getDisputeStorage());
                    disputes.add(dispute);
                    sendPeerOpenedDisputeMessage(dispute);
                } else {
                    log.warn("We got a dispute already open for that trade and trading peer.\n" +
                            "TradeId = " + dispute.getTradeId());
                }
            } else {
                log.warn("We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId());
            }
        } else {
            log.error("Trader received openNewDisputeMessage. That must never happen.");
        }
    }

    // not dispute requester receives that from arbitrator
    private void onPeerOpenedDisputeMessage(PeerOpenedDisputeMessage peerOpenedDisputeMessage) {
        Dispute dispute = peerOpenedDisputeMessage.getDispute();
        if (!isArbitrator(dispute)) {
            if (!disputes.contains(dispute)) {
                final Optional<Dispute> storedDisputeOptional = findDispute(dispute.getTradeId(), dispute.getTraderId());
                if (!storedDisputeOptional.isPresent()) {
                    dispute.setStorage(getDisputeStorage());
                    disputes.add(dispute);
                    Optional<Trade> tradeOptional = tradeManager.getTradeById(dispute.getTradeId());
                    if (tradeOptional.isPresent())
                        tradeOptional.get().setDisputeState(Trade.DisputeState.DISPUTE_STARTED_BY_PEER);
                } else {
                    log.warn("We got a dispute already open for that trade and trading peer.\n" +
                            "TradeId = " + dispute.getTradeId());
                }
            } else {
                log.warn("We got a dispute msg what we have already stored. TradeId = " + dispute.getTradeId());
            }
        } else {
            log.error("Arbitrator received peerOpenedDisputeMessage. That must never happen.");
        }
    }

    // a trader can receive a msg from the arbitrator or the arbitrator form a trader. Trader to trader is not allowed.
    private void onDisputeDirectMessage(DisputeCommunicationMessage disputeCommunicationMessage) {
        Log.traceCall("disputeCommunicationMessage " + disputeCommunicationMessage);
        final String tradeId = disputeCommunicationMessage.getTradeId();
        Optional<Dispute> disputeOptional = findDispute(tradeId, disputeCommunicationMessage.getTraderId());
        final String uid = disputeCommunicationMessage.getUid();
        if (disputeOptional.isPresent()) {
            cleanupRetryMap(uid);

            Dispute dispute = disputeOptional.get();
            if (!dispute.getDisputeCommunicationMessages().contains(disputeCommunicationMessage))
                dispute.addDisputeMessage(disputeCommunicationMessage);
            else
                log.warn("We got a disputeCommunicationMessage what we have already stored. TradeId = " + tradeId);
        } else {
            log.debug("We got a disputeCommunicationMessage but we don't have a matching dispute. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                Timer timer = UserThread.runAfter(() -> onDisputeDirectMessage(disputeCommunicationMessage), 1);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a disputeCommunicationMessage after we already repeated to apply the message after a delay. That should never happen. TradeId = " + tradeId);
            }
        }
    }

    // We get that message at both peers. The dispute object is in context of the trader
    private void onDisputeResultMessage(DisputeResultMessage disputeResultMessage) {
        DisputeResult disputeResult = disputeResultMessage.getDisputeResult();
        if (!isArbitrator(disputeResult)) {
            final String tradeId = disputeResult.getTradeId();
            Optional<Dispute> disputeOptional = findDispute(tradeId, disputeResult.getTraderId());
            final String uid = disputeResultMessage.getUid();
            if (disputeOptional.isPresent()) {
                cleanupRetryMap(uid);

                Dispute dispute = disputeOptional.get();

                DisputeCommunicationMessage disputeCommunicationMessage = disputeResult.getDisputeCommunicationMessage();
                if (!dispute.getDisputeCommunicationMessages().contains(disputeCommunicationMessage))
                    dispute.addDisputeMessage(disputeCommunicationMessage);
                else
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
                            try {
                                log.debug("do payout Transaction ");
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
                                log.debug("broadcast committedDisputedPayoutTx");
                                tradeWalletService.broadcastTx(committedDisputedPayoutTx, new FutureCallback<Transaction>() {
                                    @Override
                                    public void onSuccess(Transaction transaction) {
                                        log.debug("BroadcastTx succeeded. Transaction:" + transaction);

                                        // after successful publish we send peer the tx

                                        dispute.setDisputePayoutTxId(transaction.getHashAsString());
                                        sendPeerPublishedPayoutTxMessage(transaction, dispute, contract);

                                        // set state after payout as we call swapTradeEntryToAvailableEntry
                                        if (tradeManager.getTradeById(dispute.getTradeId()).isPresent())
                                            tradeManager.closeDisputedTrade(dispute.getTradeId());
                                        else {
                                            Optional<OpenOffer> openOfferOptional = openOfferManager.getOpenOfferById(dispute.getTradeId());
                                            if (openOfferOptional.isPresent())
                                                openOfferManager.closeOpenOffer(openOfferOptional.get().getOffer());
                                        }
                                    }

                                    @Override
                                    public void onFailure(@NotNull Throwable t) {
                                        log.error(t.getMessage());
                                    }
                                });
                            } catch (AddressFormatException | WalletException | TransactionVerificationException e) {
                                e.printStackTrace();
                                log.error("Error at traderSignAndFinalizeDisputedPayoutTx " + e.getMessage());
                                throw new RuntimeException("Error at traderSignAndFinalizeDisputedPayoutTx " + e.toString());
                            }
                        } else {
                            log.warn("DepositTx is null. TradeId = " + tradeId);
                        }
                    } else {
                        log.warn("We got already a payout tx. That might be the case if the other peer did not get the " +
                                "payout tx and opened a dispute. TradeId = " + tradeId);
                        dispute.setDisputePayoutTxId(payoutTx.getHashAsString());
                        sendPeerPublishedPayoutTxMessage(payoutTx, dispute, contract);
                    }
                } else {
                    log.trace("We don't publish the tx as we are not the winning party.");
                    // Clean up tangling trades
                    if (dispute.disputeResultProperty().get() != null &&
                            dispute.isClosed() &&
                            tradeManager.getTradeById(dispute.getTradeId()).isPresent())
                        tradeManager.closeDisputedTrade(dispute.getTradeId());
                }
            } else {
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
            }
        } else {
            log.error("Arbitrator received disputeResultMessage. That must never happen.");
        }
    }

    // losing trader or in case of 50/50 the seller gets the tx sent from the winner or buyer
    private void onDisputedPayoutTxMessage(PeerPublishedDisputePayoutTxMessage peerPublishedDisputePayoutTxMessage) {
        final String uid = peerPublishedDisputePayoutTxMessage.getUid();
        final String tradeId = peerPublishedDisputePayoutTxMessage.getTradeId();
        Optional<Dispute> disputeOptional = findOwnDispute(tradeId);
        if (disputeOptional.isPresent()) {
            cleanupRetryMap(uid);

            Transaction walletTx = tradeWalletService.addTxToWallet(peerPublishedDisputePayoutTxMessage.getTransaction());
            disputeOptional.get().setDisputePayoutTxId(walletTx.getHashAsString());
            BtcWalletService.printTx("Disputed payoutTx received from peer", walletTx);
        } else {
            log.debug("We got a peerPublishedPayoutTxMessage but we don't have a matching dispute. TradeId = " + tradeId);
            if (!delayMsgMap.containsKey(uid)) {
                // We delay 3 sec. to be sure the close msg gets added first
                Timer timer = UserThread.runAfter(() -> onDisputedPayoutTxMessage(peerPublishedDisputePayoutTxMessage), 3);
                delayMsgMap.put(uid, timer);
            } else {
                log.warn("We got a peerPublishedPayoutTxMessage after we already repeated to apply the message after a delay. " +
                        "That should never happen. TradeId = " + tradeId);
            }
        }
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
                walletService.getOrCreateAddressEntry(AddressEntry.Context.ARBITRATOR).getPubKey());
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
