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

package bisq.network.p2p;

import bisq.network.Socks5ProxyProvider;
import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.messaging.DecryptedMailboxListener;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.network.SetupListener;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.peers.getdata.RequestDataManager;
import bisq.network.p2p.peers.keepalive.KeepAliveManager;
import bisq.network.p2p.peers.peerexchange.Peer;
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;

import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;

import java.security.PublicKey;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class P2PService implements SetupListener, MessageListener, ConnectionListener, RequestDataManager.Listener,
        HashMapChangedListener, PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);
    public static final int MAX_CONNECTIONS_DEFAULT = 12;

    private final SeedNodeRepository seedNodeRepository;
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    @Getter
    private final Broadcaster broadcaster;
    private final P2PDataStorage p2PDataStorage;
    private final RequestDataManager requestDataManager;
    private final PeerExchangeManager peerExchangeManager;

    @SuppressWarnings("FieldCanBeLocal")
    private final MonadicBinding<Boolean> networkReadyBinding;
    private final Set<DecryptedDirectMessageListener> decryptedDirectMessageListeners = new CopyOnWriteArraySet<>();
    private final Set<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    private final Set<P2PServiceListener> p2pServiceListeners = new CopyOnWriteArraySet<>();
    private final Map<String, ProtectedMailboxStorageEntry> mailboxMap = new HashMap<>();
    private final Set<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    private final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty preliminaryDataReceived = new SimpleBooleanProperty();
    private final IntegerProperty numConnectedPeers = new SimpleIntegerProperty(0);

    private volatile boolean shutDownInProgress;
    private boolean shutDownComplete;
    private final Subscription networkReadySubscription;
    private boolean isBootstrapped;
    private final KeepAliveManager keepAliveManager;
    private final Socks5ProxyProvider socks5ProxyProvider;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called also from SeedNodeP2PService
    @Inject
    public P2PService(NetworkNode networkNode,
                      PeerManager peerManager,
                      P2PDataStorage p2PDataStorage,
                      RequestDataManager requestDataManager,
                      PeerExchangeManager peerExchangeManager,
                      KeepAliveManager keepAliveManager,
                      Broadcaster broadcaster,
                      SeedNodeRepository seedNodeRepository,
                      Socks5ProxyProvider socks5ProxyProvider,
                      EncryptionService encryptionService,
                      KeyRing keyRing) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.p2PDataStorage = p2PDataStorage;
        this.requestDataManager = requestDataManager;
        this.peerExchangeManager = peerExchangeManager;
        this.keepAliveManager = keepAliveManager;
        this.broadcaster = broadcaster;
        this.seedNodeRepository = seedNodeRepository;
        this.socks5ProxyProvider = socks5ProxyProvider;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;

        this.networkNode.addConnectionListener(this);
        this.networkNode.addMessageListener(this);
        this.p2PDataStorage.addHashMapChangedListener(this);
        this.requestDataManager.addListener(this);

        // We need to have both the initial data delivered and the hidden service published
        networkReadyBinding = EasyBind.combine(hiddenServicePublished, preliminaryDataReceived,
                (hiddenServicePublished, preliminaryDataReceived)
                        -> hiddenServicePublished && preliminaryDataReceived);
        networkReadySubscription = networkReadyBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                onNetworkReady();
        });
    }

    @Override
    public void readPersisted() {
        p2PDataStorage.readPersisted();
        peerManager.readPersisted();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void start(@Nullable P2PServiceListener listener) {
        if (listener != null)
            addP2PServiceListener(listener);

        networkNode.start(this);
    }

    public void onAllServicesInitialized() {
        if (networkNode.getNodeAddress() != null) {
            maybeProcessAllMailboxEntries();
        } else {
            // If our HS is still not published
            networkNode.nodeAddressProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null)
                    maybeProcessAllMailboxEntries();
            });
        }
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        if (!shutDownInProgress) {
            shutDownInProgress = true;

            shutDownResultHandlers.add(shutDownCompleteHandler);

            if (p2PDataStorage != null)
                p2PDataStorage.shutDown();

            if (peerManager != null)
                peerManager.shutDown();

            if (broadcaster != null)
                broadcaster.shutDown();

            if (requestDataManager != null)
                requestDataManager.shutDown();

            if (peerExchangeManager != null)
                peerExchangeManager.shutDown();

            if (keepAliveManager != null)
                keepAliveManager.shutDown();

            if (networkReadySubscription != null)
                networkReadySubscription.unsubscribe();

            if (networkNode != null) {
                networkNode.shutDown(() -> {
                    shutDownResultHandlers.stream().forEach(Runnable::run);
                    shutDownComplete = true;
                });
            } else {
                shutDownResultHandlers.stream().forEach(Runnable::run);
                shutDownComplete = true;
            }
        } else {
            log.debug("shutDown already in progress");
            if (shutDownComplete) {
                shutDownCompleteHandler.run();
            } else {
                shutDownResultHandlers.add(shutDownCompleteHandler);
            }
        }
    }


    /**
     * Startup sequence:
     * <p/>
     * Variant 1 (normal expected mode):
     * onTorNodeReady -> requestDataManager.firstDataRequestFromAnySeedNode()
     * RequestDataManager.Listener.onDataReceived && onHiddenServicePublished -> onNetworkReady()
     * <p/>
     * Variant 2 (no seed node available):
     * onTorNodeReady -> requestDataManager.firstDataRequestFromAnySeedNode
     * retry after 20-30 sec until we get at least one seed node connected
     * RequestDataManager.Listener.onDataReceived && onHiddenServicePublished -> onNetworkReady()
     */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onTorNodeReady() {
        socks5ProxyProvider.setSocks5ProxyInternal(networkNode);

        boolean seedNodesAvailable = requestDataManager.requestPreliminaryData();

        keepAliveManager.start();
        p2pServiceListeners.stream().forEach(SetupListener::onTorNodeReady);

        if (!seedNodesAvailable) {
            isBootstrapped = true;
            maybeProcessAllMailboxEntries();
            p2pServiceListeners.stream().forEach(P2PServiceListener::onNoSeedNodeAvailable);
        }
    }

    @Override
    public void onHiddenServicePublished() {
        checkArgument(networkNode.getNodeAddress() != null, "Address must be set when we have the hidden service ready");

        hiddenServicePublished.set(true);

        p2pServiceListeners.stream().forEach(SetupListener::onHiddenServicePublished);
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        p2pServiceListeners.stream().forEach(e -> e.onSetupFailed(throwable));
    }

    @Override
    public void onRequestCustomBridges() {
        p2pServiceListeners.stream().forEach(SetupListener::onRequestCustomBridges);
    }

    // Called from networkReadyBinding
    private void onNetworkReady() {
        networkReadySubscription.unsubscribe();

        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeAddressOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");

        requestDataManager.requestUpdateData();
        /*if (Capabilities.app.containsAll(Capability.SEED_NODE))
            UserThread.runPeriodically(() -> requestDataManager.requestUpdateData(), 1, TimeUnit.HOURS);*/

        // If we start up first time we don't have any peers so we need to request from seed node.
        // As well it can be that the persisted peer list is outdated with dead peers.
        UserThread.runAfter(() -> {
            peerExchangeManager.requestReportedPeersFromSeedNodes(seedNodeOfPreliminaryDataRequest.get());
        }, 100, TimeUnit.MILLISECONDS);

        // If we have reported or persisted peers we try to connect to those
        UserThread.runAfter(peerExchangeManager::initialRequestPeersFromReportedOrPersistedPeers, 300, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestDataManager.Listener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPreliminaryDataReceived() {
        checkArgument(!preliminaryDataReceived.get(), "preliminaryDataReceived was already set before.");

        preliminaryDataReceived.set(true);
    }

    @Override
    public void onUpdatedDataReceived() {
        if (!isBootstrapped) {
            isBootstrapped = true;
            maybeProcessAllMailboxEntries();
            p2pServiceListeners.stream().forEach(P2PServiceListener::onUpdatedDataReceived);
            p2PDataStorage.onBootstrapComplete();
        }
    }

    @Override
    public void onNoSeedNodeAvailable() {
        p2pServiceListeners.stream().forEach(P2PServiceListener::onNoSeedNodeAvailable);
    }

    @Override
    public void onNoPeersAvailable() {
        p2pServiceListeners.stream().forEach(P2PServiceListener::onNoPeersAvailable);
    }

    @Override
    public void onDataReceived() {
        p2pServiceListeners.stream().forEach(P2PServiceListener::onDataReceived);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
        numConnectedPeers.set(networkNode.getAllConnections().size());
        //TODO check if still needed and why
        UserThread.runAfter(() -> numConnectedPeers.set(networkNode.getAllConnections().size()), 3);
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        numConnectedPeers.set(networkNode.getAllConnections().size());
        //TODO check if still needed and why
        UserThread.runAfter(() -> numConnectedPeers.set(networkNode.getAllConnections().size()), 3);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof PrefixedSealedAndSignedMessage) {
            // Seed nodes don't have set the encryptionService
            try {
                PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = (PrefixedSealedAndSignedMessage) networkEnvelope;
                if (verifyAddressPrefixHash(prefixedSealedAndSignedMessage)) {
                    // We set connectionType to that connection to avoid that is get closed when
                    // we get too many connection attempts.
                    connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                    log.debug("Try to decrypt...");
                    DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerify(
                            prefixedSealedAndSignedMessage.getSealedAndSigned());

                    log.debug("\n\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n" +
                            "Decrypted SealedAndSignedMessage:\ndecryptedMsgWithPubKey={}"
                            + "\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n", decryptedMessageWithPubKey);
                    if (connection.getPeersNodeAddressOptional().isPresent())
                        decryptedDirectMessageListeners.forEach(
                                e -> e.onDirectMessage(decryptedMessageWithPubKey, connection.getPeersNodeAddressOptional().get()));
                    else
                        log.error("peersNodeAddress is not available at onMessage.");
                } else {
                    log.debug("Wrong receiverAddressMaskHash. The message is not intended for us.");
                }
            } catch (CryptoException e) {
                log.debug(networkEnvelope.toString());
                log.debug(e.toString());
                log.debug("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                        "That is expected if the message is not intended for us.");
            } catch (ProtobufferException e) {
                log.error("Protobuffer data could not be processed: {}", e.toString());
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        protectedStorageEntries.forEach(protectedStorageEntry -> {
            if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                processMailboxEntry((ProtectedMailboxStorageEntry) protectedStorageEntry);
        });
    }

    @Override
    public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) { }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // DirectMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    // TODO OfferAvailabilityResponse is called twice!
    public void sendEncryptedDirectMessage(NodeAddress peerNodeAddress, PubKeyRing pubKeyRing, NetworkEnvelope message,
                                           SendDirectMessageListener sendDirectMessageListener) {
        checkNotNull(peerNodeAddress, "PeerAddress must not be null (sendEncryptedDirectMessage)");
        if (isBootstrapped()) {
            doSendEncryptedDirectMessage(peerNodeAddress, pubKeyRing, message, sendDirectMessageListener);
        } else {
            throw new NetworkNotReadyException();
        }
    }

    private void doSendEncryptedDirectMessage(@NotNull NodeAddress peersNodeAddress, PubKeyRing pubKeyRing, NetworkEnvelope message,
                                              SendDirectMessageListener sendDirectMessageListener) {
        log.debug("Send encrypted direct message {} to peer {}",
                message.getClass().getSimpleName(), peersNodeAddress);

        checkNotNull(peersNodeAddress, "Peer node address must not be null at doSendEncryptedDirectMessage");

        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at doSendEncryptedDirectMessage");

        if (capabilityRequiredAndCapabilityNotSupported(peersNodeAddress, message)) {
            sendDirectMessageListener.onFault("We did not send the EncryptedMessage " +
                    "because the peer does not support the capability.");
            return;
        }

        try {
            log.debug("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                    "Encrypt message:\nmessage={}"
                    + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);
            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                    networkNode.getNodeAddress(),
                    encryptionService.encryptAndSign(pubKeyRing, message),
                    peersNodeAddress.getAddressPrefixHash(),
                    UUID.randomUUID().toString());
            SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendDirectMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.error(throwable.toString());
                    throwable.printStackTrace();
                    sendDirectMessageListener.onFault(throwable.toString());
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error(message.toString());
            log.error(e.toString());
            sendDirectMessageListener.onFault(e.toString());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MailboxMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processMailboxEntry(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        NodeAddress nodeAddress = networkNode.getNodeAddress();
        // Seed nodes don't receive mailbox network_messages
        if (nodeAddress != null && !seedNodeRepository.isSeedNode(nodeAddress)) {
            MailboxStoragePayload mailboxStoragePayload = protectedMailboxStorageEntry.getMailboxStoragePayload();
            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = mailboxStoragePayload.getPrefixedSealedAndSignedMessage();
            if (verifyAddressPrefixHash(prefixedSealedAndSignedMessage)) {
                try {
                    DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerify(
                            prefixedSealedAndSignedMessage.getSealedAndSigned());
                    if (decryptedMessageWithPubKey.getNetworkEnvelope() instanceof MailboxMessage) {
                        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
                        NodeAddress senderNodeAddress = mailboxMessage.getSenderNodeAddress();
                        checkNotNull(senderNodeAddress, "senderAddress must not be null for mailbox network_messages");

                        mailboxMap.put(mailboxMessage.getUid(), protectedMailboxStorageEntry);
                        log.info("Received a {} mailbox message with messageUid {} and senderAddress {}", mailboxMessage.getClass().getSimpleName(), mailboxMessage.getUid(), senderNodeAddress);
                        decryptedMailboxListeners.forEach(
                                e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, senderNodeAddress));
                    } else {
                        log.warn("tryDecryptMailboxData: Expected MailboxMessage but got other type. " +
                                "decryptedMsgWithPubKey.message={}", decryptedMessageWithPubKey.getNetworkEnvelope());
                    }
                } catch (CryptoException e) {
                    log.debug(e.toString());
                    log.debug("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                            "That is expected if the message is not intended for us.");
                } catch (ProtobufferException e) {
                    log.error("Protobuffer data could not be processed: {}", e.toString());
                }
            } else {
                log.trace("Wrong blurredAddressHash. The message is not intended for us.");
            }
        }
    }

    public void sendEncryptedMailboxMessage(NodeAddress peersNodeAddress, PubKeyRing peersPubKeyRing,
                                            NetworkEnvelope message,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        checkNotNull(peersNodeAddress,
                "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing),
                "We got own keyring instead of that from peer");

        if (!isBootstrapped())
            throw new NetworkNotReadyException();

        if (networkNode.getAllConnections().isEmpty()) {
            sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                    "Please check your internet connection.");
            return;
        }

        if (capabilityRequiredAndCapabilityNotSupported(peersNodeAddress, message)) {
            sendMailboxMessageListener.onFault("We did not send the EncryptedMailboxMessage " +
                    "because the peer does not support the capability.");
            return;
        }

        try {
            log.debug("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                    "Encrypt message:\nmessage={}"
                    + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);

            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                    networkNode.getNodeAddress(),
                    encryptionService.encryptAndSign(peersPubKeyRing, message),
                    peersNodeAddress.getAddressPrefixHash(),
                    UUID.randomUUID().toString());

            log.debug("sendEncryptedMailboxMessage msg={},  peersNodeAddress={}", message, peersNodeAddress);
            SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendMailboxMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    PublicKey receiverStoragePublicKey = peersPubKeyRing.getSignaturePubKey();
                    addMailboxData(new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                                    keyRing.getSignatureKeyPair().getPublic(),
                                    receiverStoragePublicKey),
                            receiverStoragePublicKey,
                            sendMailboxMessageListener);
                }
            });
        } catch (CryptoException e) {
            log.error("sendEncryptedMessage failed");
            e.printStackTrace();
            sendMailboxMessageListener.onFault("sendEncryptedMailboxMessage failed " + e);
        }
    }

    private boolean capabilityRequiredAndCapabilityNotSupported(NodeAddress peersNodeAddress, NetworkEnvelope message) {
        if (!(message instanceof CapabilityRequiringPayload))
            return false;

        // We only expect AckMessage so far
        if (!(message instanceof AckMessage))
            log.warn("We got a CapabilityRequiringPayload for the mailbox message which is not a AckMessage. " +
                    "peersNodeAddress={}", peersNodeAddress);

        Set<Peer> allPeers = peerManager.getPersistedPeers();
        allPeers.addAll(peerManager.getReportedPeers());
        allPeers.addAll(peerManager.getLivePeers(null));
        // We might have multiple entries of the same peer without the supportedCapabilities field set if we received
        // it from old versions, so we filter those.
        Optional<Peer> optionalPeer = allPeers.stream()
                .filter(peer -> peer.getNodeAddress().equals(peersNodeAddress))
                .filter(peer -> !peer.getCapabilities().isEmpty())
                .findAny();
        if (optionalPeer.isPresent()) {
            boolean result = optionalPeer.get().getCapabilities().containsAll(((CapabilityRequiringPayload) message).getRequiredCapabilities());

            if (!result)
                log.warn("We don't send the message because the peer does not support the required capability. " +
                        "peersNodeAddress={}", peersNodeAddress);

            return !result;
        }

        log.warn("We don't have the peer in our persisted peers so we don't know their capabilities. " +
                "We decide to not sent the msg. peersNodeAddress={}", peersNodeAddress);
        return true;

    }

    private void maybeProcessAllMailboxEntries() {
        if (isBootstrapped) {
            p2PDataStorage.getMap().values().forEach(protectedStorageEntry -> {
                if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                    processMailboxEntry((ProtectedMailboxStorageEntry) protectedStorageEntry);
            });
        }
    }

    private void addMailboxData(MailboxStoragePayload expirableMailboxStoragePayload,
                                PublicKey receiversPublicKey,
                                SendMailboxMessageListener sendMailboxMessageListener) {
        if (isBootstrapped()) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                            expirableMailboxStoragePayload,
                            keyRing.getSignatureKeyPair(),
                            receiversPublicKey);

                    BroadcastHandler.Listener listener = new BroadcastHandler.Listener() {
                        @Override
                        public void onBroadcasted(BroadcastMessage message, int numOfCompletedBroadcasts) {
                        }

                        @Override
                        public void onBroadcastedToFirstPeer(BroadcastMessage message) {
                            // The reason for that check was to separate different callback for different send calls.
                            // We only want to notify our sendMailboxMessageListener for the calls he is interested in.
                            if (message instanceof AddDataMessage &&
                                    ((AddDataMessage) message).getProtectedStorageEntry().equals(protectedMailboxStorageEntry)) {
                                // We delay a bit to give more time for sufficient propagation in the P2P network.
                                // This should help to avoid situations where a user closes the app too early and the msg
                                // does not arrive.
                                // We could use onBroadcastCompleted instead but it might take too long if one peer
                                // is very badly connected.
                                // TODO We could check for a certain threshold of no. of incoming network_messages of the same msg
                                // to see how well it is propagated. BitcoinJ uses such an approach for tx propagation.
                                UserThread.runAfter(() -> {
                                    log.info("Broadcasted to first peer (3 sec. ago):  Message = {}", Utilities.toTruncatedString(message));
                                    sendMailboxMessageListener.onStoredInMailbox();
                                }, 3);
                            }
                        }

                        @Override
                        public void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts) {
                            log.info("Broadcast completed: Sent to {} peers (failed: {}). Message = {}",
                                    numOfCompletedBroadcasts, numOfFailedBroadcasts, Utilities.toTruncatedString(message));
                            if (numOfCompletedBroadcasts == 0)
                                sendMailboxMessageListener.onFault("Broadcast completed without any successful broadcast");
                        }

                        @Override
                        public void onBroadcastFailed(String errorMessage) {
                            // TODO investigate why not sending sendMailboxMessageListener.onFault. Related probably
                            // to the logic from BroadcastHandler.sendToPeer
                        }
                    };
                    boolean result = p2PDataStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, networkNode.getNodeAddress(), listener);
                    if (!result) {
                        sendMailboxMessageListener.onFault("Data already exists in our local database");

                        // This should only fail if there are concurrent calls to addProtectedStorageEntry with the
                        // same ProtectedMailboxStorageEntry. This is an unexpected use case so if it happens we
                        // want to see it, but it is not worth throwing an exception.
                        log.error("Unexpected state: adding mailbox message that already exists.");
                    }
                } catch (CryptoException e) {
                    log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                }
            } else {
                sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                        "Please check your internet connection.");
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public void removeEntryFromMailbox(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        // We need to delay a bit to avoid that we remove our msg then get it from other peers again and reapply it again.
        // If we delay the removal we have better chances that repeated network_messages we got from other peers are already filtered
        // at the P2PService layer.
        // Though we have to check in the client classes to not apply the same message again as there is no guarantee
        // when we would get a message again from the network.
        try {
            UserThread.runAfter(() -> delayedRemoveEntryFromMailbox(decryptedMessageWithPubKey), 2);
        } catch (NetworkNotReadyException t) {
            // If we called too early it might throw a NetworkNotReadyException. We will try again
            try {
                UserThread.runAfter(() -> delayedRemoveEntryFromMailbox(decryptedMessageWithPubKey), 60);
            } catch (NetworkNotReadyException ignore) {
                log.warn("We tried to call delayedRemoveEntryFromMailbox 60 sec. after we received an " +
                        "NetworkNotReadyException but it failed again. We give up here.");
            }
        }
    }

    private void delayedRemoveEntryFromMailbox(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        if (!isBootstrapped()) {
            // We don't throw an NetworkNotReadyException here.
            // This case should not happen anyway as we check for isBootstrapped in the callers.
            log.warn("You must have bootstrapped before adding data to the P2P network.");
        }

        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
        String uid = mailboxMessage.getUid();
        if (mailboxMap.containsKey(uid)) {
            ProtectedMailboxStorageEntry mailboxData = mailboxMap.get(uid);
            if (mailboxData != null && mailboxData.getProtectedStoragePayload() instanceof MailboxStoragePayload) {
                MailboxStoragePayload expirableMailboxStoragePayload = (MailboxStoragePayload) mailboxData.getProtectedStoragePayload();
                PublicKey receiversPubKey = mailboxData.getReceiversPubKey();
                checkArgument(receiversPubKey.equals(keyRing.getSignatureKeyPair().getPublic()),
                        "receiversPubKey is not matching with our key. That must not happen.");
                try {
                    ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                            expirableMailboxStoragePayload,
                            keyRing.getSignatureKeyPair(),
                            receiversPubKey);
                    p2PDataStorage.remove(protectedMailboxStorageEntry, networkNode.getNodeAddress());
                } catch (CryptoException e) {
                    log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                }

                mailboxMap.remove(uid);
                log.info("Removed successfully decryptedMsgWithPubKey. uid={}", uid);
            }
        } else {
            log.warn("uid for mailbox entry not found in mailboxMap." + "uid={}", uid);
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addPersistableNetworkPayload(PersistableNetworkPayload payload, boolean reBroadcast) {
        return p2PDataStorage.addPersistableNetworkPayload(payload, networkNode.getNodeAddress(), reBroadcast);
    }

    public boolean addProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload) {
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedStorageEntry(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.addProtectedStorageEntry(protectedStorageEntry, networkNode.getNodeAddress(), null);
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean refreshTTL(ProtectedStoragePayload protectedStoragePayload) {
        if (isBootstrapped()) {
            try {
                RefreshOfferMessage refreshTTLMessage = p2PDataStorage.getRefreshTTLMessage(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.refreshTTL(refreshTTLMessage, networkNode.getNodeAddress());
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean removeData(ProtectedStoragePayload protectedStoragePayload) {
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedStorageEntry(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.remove(protectedStorageEntry, networkNode.getNodeAddress());
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDecryptedDirectMessageListener(DecryptedDirectMessageListener listener) {
        decryptedDirectMessageListeners.add(listener);
    }

    public void removeDecryptedDirectMessageListener(DecryptedDirectMessageListener listener) {
        decryptedDirectMessageListeners.remove(listener);
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        decryptedMailboxListeners.add(listener);
    }

    public void addP2PServiceListener(P2PServiceListener listener) {
        p2pServiceListeners.add(listener);
    }

    public void removeP2PServiceListener(P2PServiceListener listener) {
        if (p2pServiceListeners.contains(listener))
            p2pServiceListeners.remove(listener);
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PDataStorage.addHashMapChangedListener(hashMapChangedListener);
    }

    public void removeHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        p2PDataStorage.removeHashMapChangedListener(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isBootstrapped() {
        return isBootstrapped;
    }

    public NetworkNode getNetworkNode() {
        return networkNode;
    }

    @Nullable
    public NodeAddress getAddress() {
        return networkNode.getNodeAddress();
    }

    public ReadOnlyIntegerProperty getNumConnectedPeers() {
        return numConnectedPeers;
    }

    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getDataMap() {
        return p2PDataStorage.getMap();
    }

    @VisibleForTesting
    public P2PDataStorage getP2PDataStorage() {
        return p2PDataStorage;
    }

    @VisibleForTesting
    public PeerManager getPeerManager() {
        return peerManager;
    }

    @VisibleForTesting
    public KeyRing getKeyRing() {
        return keyRing;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean verifyAddressPrefixHash(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage) {
        if (networkNode.getNodeAddress() != null) {
            byte[] blurredAddressHash = networkNode.getNodeAddress().getAddressPrefixHash();
            return blurredAddressHash != null &&
                    Arrays.equals(blurredAddressHash, prefixedSealedAndSignedMessage.getAddressPrefixHash());
        } else {
            log.debug("myOnionAddress is null at verifyAddressPrefixHash. That is expected at startup.");
            return false;
        }
    }
}
