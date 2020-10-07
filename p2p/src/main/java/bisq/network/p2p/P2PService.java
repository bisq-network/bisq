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
import bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import bisq.network.p2p.seed.SeedNodeRepository;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;

import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import com.google.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

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
    @Getter
    private final Map<String, Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey>> mailboxMap = new HashMap<>();
    private final Set<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    private final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty preliminaryDataReceived = new SimpleBooleanProperty();
    private final IntegerProperty numConnectedPeers = new SimpleIntegerProperty(0);

    private final Subscription networkReadySubscription;
    private boolean isBootstrapped;
    private final KeepAliveManager keepAliveManager;
    private final Socks5ProxyProvider socks5ProxyProvider;

    @Getter
    private static NodeAddress myNodeAddress;


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
            myNodeAddress = networkNode.getNodeAddress();
        } else {
            // If our HS is still not published
            networkNode.nodeAddressProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    myNodeAddress = networkNode.getNodeAddress();
                }
            });
        }
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        shutDownResultHandlers.add(shutDownCompleteHandler);

        // We need to make sure queued up messages are flushed out before we continue shut down other network
        // services
        if (broadcaster != null) {
            broadcaster.shutDown(this::doShutDown);
        } else {
            doShutDown();
        }
    }

    private void doShutDown() {
        if (p2PDataStorage != null) {
            p2PDataStorage.shutDown();
        }

        if (peerManager != null) {
            peerManager.shutDown();
        }

        if (requestDataManager != null) {
            requestDataManager.shutDown();
        }

        if (peerExchangeManager != null) {
            peerExchangeManager.shutDown();
        }

        if (keepAliveManager != null) {
            keepAliveManager.shutDown();
        }

        if (networkReadySubscription != null) {
            networkReadySubscription.unsubscribe();
        }

        if (networkNode != null) {
            networkNode.shutDown(() -> {
                shutDownResultHandlers.forEach(Runnable::run);
            });
        } else {
            shutDownResultHandlers.forEach(Runnable::run);
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
        p2pServiceListeners.forEach(SetupListener::onTorNodeReady);

        if (!seedNodesAvailable) {
            isBootstrapped = true;
            // As we do not expect a updated data request response we start here with addHashMapChangedListenerAndApply
            addHashMapChangedListenerAndApply();
            p2pServiceListeners.forEach(P2PServiceListener::onNoSeedNodeAvailable);
        }
    }

    @Override
    public void onHiddenServicePublished() {
        checkArgument(networkNode.getNodeAddress() != null, "Address must be set when we have the hidden service ready");

        hiddenServicePublished.set(true);

        p2pServiceListeners.forEach(SetupListener::onHiddenServicePublished);
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        p2pServiceListeners.forEach(e -> e.onSetupFailed(throwable));
    }

    @Override
    public void onRequestCustomBridges() {
        p2pServiceListeners.forEach(SetupListener::onRequestCustomBridges);
    }

    // Called from networkReadyBinding
    private void onNetworkReady() {
        networkReadySubscription.unsubscribe();

        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeAddressOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");

        requestDataManager.requestUpdateData();

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
            // Only now we start listening and processing. The p2PDataStorage is our cache for data we have received
            // after the hidden service was ready.
            addHashMapChangedListenerAndApply();
            p2pServiceListeners.forEach(P2PServiceListener::onUpdatedDataReceived);
            p2PDataStorage.onBootstrapComplete();
        }
    }

    @Override
    public void onNoSeedNodeAvailable() {
        p2pServiceListeners.forEach(P2PServiceListener::onNoSeedNodeAvailable);
    }

    @Override
    public void onNoPeersAvailable() {
        p2pServiceListeners.forEach(P2PServiceListener::onNoPeersAvailable);
    }

    @Override
    public void onDataReceived() {
        p2pServiceListeners.forEach(P2PServiceListener::onDataReceived);
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
            PrefixedSealedAndSignedMessage sealedMsg = (PrefixedSealedAndSignedMessage) networkEnvelope;
            connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);
            try {
                DecryptedMessageWithPubKey decryptedMsg = encryptionService.decryptAndVerify(sealedMsg.getSealedAndSigned());
                connection.getPeersNodeAddressOptional().ifPresentOrElse(nodeAddress ->
                                decryptedDirectMessageListeners.forEach(e -> e.onDirectMessage(decryptedMsg, nodeAddress)),
                        () -> {
                            log.error("peersNodeAddress is expected to be available at onMessage for " +
                                    "processing PrefixedSealedAndSignedMessage.");
                        });
            } catch (CryptoException e) {
                log.warn("Decryption of a direct message failed. This is not expected as the " +
                        "direct message was sent to our node.");
            } catch (ProtobufferException e) {
                log.error("ProtobufferException at decryptAndVerify: {}", e.toString());
                e.getStackTrace();
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation for ProtectedStorageEntry items
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addHashMapChangedListenerAndApply() {
        p2PDataStorage.addHashMapChangedListener(this);
        onAdded(p2PDataStorage.getMap().values());
    }

    @Override
    public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        Collection<ProtectedMailboxStorageEntry> entries = protectedStorageEntries.stream()
                .filter(e -> e instanceof ProtectedMailboxStorageEntry)
                .map(e -> (ProtectedMailboxStorageEntry) e)
                .filter(e -> networkNode.getNodeAddress() != null)
                .filter(e -> !seedNodeRepository.isSeedNode(networkNode.getNodeAddress())) // Seed nodes don't expect mailbox messages
                .collect(Collectors.toSet());
        if (entries.size() > 1) {
            threadedBatchProcessMailboxEntries(entries);
        } else if (entries.size() == 1) {
            processSingleMailboxEntry(entries);
        }
    }

    private void processSingleMailboxEntry(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        var decryptedEntries = new ArrayList<>(getDecryptedEntries(protectedMailboxStorageEntries));
        checkArgument(decryptedEntries.size() == 1);
        storeMailboxDataAndNotifyListeners(decryptedEntries.get(0));
    }

    // We run the batch processing of all mailbox messages we have received at startup in a thread to not block the UI.
    // For about 1000 messages decryption takes about 1 sec.
    private void threadedBatchProcessMailboxEntries(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        ListeningExecutorService executor = Utilities.getSingleThreadListeningExecutor("processMailboxEntry-" + new Random().nextInt(1000));
        long ts = System.currentTimeMillis();
        ListenableFuture<Set<Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey>>> future = executor.submit(() -> {
            var decryptedEntries = getDecryptedEntries(protectedMailboxStorageEntries);
            log.info("Batch processing of {} mailbox entries took {} ms",
                    protectedMailboxStorageEntries.size(),
                    System.currentTimeMillis() - ts);
            return decryptedEntries;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Set<Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey>> decryptedEntries) {
                UserThread.execute(() -> decryptedEntries.forEach(e -> storeMailboxDataAndNotifyListeners(e)));
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error(throwable.toString());
            }
        }, MoreExecutors.directExecutor());
    }

    private Set<Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey>> getDecryptedEntries(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        Set<Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey>> decryptedEntries = new HashSet<>();
        protectedMailboxStorageEntries.stream()
                .map(this::decryptProtectedMailboxStorageEntry)
                .filter(Objects::nonNull)
                .forEach(decryptedEntries::add);
        return decryptedEntries;
    }

    @Nullable
    private Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey> decryptProtectedMailboxStorageEntry(
            ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        try {
            DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerify(protectedMailboxStorageEntry
                    .getMailboxStoragePayload()
                    .getPrefixedSealedAndSignedMessage()
                    .getSealedAndSigned());
            checkArgument(decryptedMessageWithPubKey.getNetworkEnvelope() instanceof MailboxMessage);
            return new Tuple2<>(protectedMailboxStorageEntry, decryptedMessageWithPubKey);
        } catch (CryptoException ignore) {
            // Expected if message was not intended for us
        } catch (ProtobufferException e) {
            log.error(e.toString());
            e.getStackTrace();
        }
        return null;
    }

    private void storeMailboxDataAndNotifyListeners(Tuple2<ProtectedMailboxStorageEntry, DecryptedMessageWithPubKey> tuple2) {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = tuple2.second;
        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
        NodeAddress sender = mailboxMessage.getSenderNodeAddress();
        mailboxMap.put(mailboxMessage.getUid(), tuple2);
        log.info("Received a {} mailbox message with uid {} and senderAddress {}",
                mailboxMessage.getClass().getSimpleName(), mailboxMessage.getUid(), sender);
        decryptedMailboxListeners.forEach(e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, sender));
    }


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

    private void doSendEncryptedDirectMessage(@NotNull NodeAddress peersNodeAddress,
                                              PubKeyRing pubKeyRing,
                                              NetworkEnvelope message,
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

            // Prefix is not needed for direct messages but as old code is doing the verification we still need to
            // send it if peer has not updated.
            PrefixedSealedAndSignedMessage sealedMsg = getPrefixedSealedAndSignedMessage(peersNodeAddress,
                    pubKeyRing,
                    message);

            SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, sealedMsg);
            Futures.addCallback(future, new FutureCallback<>() {
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
            }, MoreExecutors.directExecutor());
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error(message.toString());
            log.error(e.toString());
            sendDirectMessageListener.onFault(e.toString());
        }
    }

    private PrefixedSealedAndSignedMessage getPrefixedSealedAndSignedMessage(NodeAddress peersNodeAddress,
                                                                             PubKeyRing pubKeyRing,
                                                                             NetworkEnvelope message) throws CryptoException {
        byte[] addressPrefixHash;
        if (peerManager.peerHasCapability(peersNodeAddress, Capability.NO_ADDRESS_PRE_FIX)) {
            // The peer has an updated version so we do not need to send the prefix.
            // We cannot use null as not updated nodes would get a nullPointer at protobuf serialisation.
            addressPrefixHash = new byte[0];
        } else {
            addressPrefixHash = peersNodeAddress.getAddressPrefixHash();
        }
        return new PrefixedSealedAndSignedMessage(
                networkNode.getNodeAddress(),
                encryptionService.encryptAndSign(pubKeyRing, message),
                addressPrefixHash,
                UUID.randomUUID().toString());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MailboxMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedMailboxMessage(NodeAddress peer, PubKeyRing peersPubKeyRing,
                                            NetworkEnvelope message,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        if (peersPubKeyRing == null) {
            log.error("sendEncryptedMailboxMessage: peersPubKeyRing is null. We ignore the call.");
            return;
        }

        checkNotNull(peer, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");

        if (!isBootstrapped())
            throw new NetworkNotReadyException();

        if (networkNode.getAllConnections().isEmpty()) {
            sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                    "Please check your internet connection.");
            return;
        }

        if (capabilityRequiredAndCapabilityNotSupported(peer, message)) {
            sendMailboxMessageListener.onFault("We did not send the EncryptedMailboxMessage " +
                    "because the peer does not support the capability.");
            return;
        }

        try {
            log.debug("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                    "Encrypt message:\nmessage={}"
                    + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);

            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = getPrefixedSealedAndSignedMessage(peer,
                    peersPubKeyRing, message);

            log.debug("sendEncryptedMailboxMessage msg={},  peersNodeAddress={}", message, peer);
            SettableFuture<Connection> future = networkNode.sendMessage(peer, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<>() {
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
            }, MoreExecutors.directExecutor());
        } catch (CryptoException e) {
            log.error("sendEncryptedMessage failed");
            e.printStackTrace();
            sendMailboxMessageListener.onFault("sendEncryptedMailboxMessage failed " + e);
        }
    }

    private boolean capabilityRequiredAndCapabilityNotSupported(NodeAddress peersNodeAddress, NetworkEnvelope message) {
        if (!(message instanceof CapabilityRequiringPayload))
            return false;

        // We might have multiple entries of the same peer without the supportedCapabilities field set if we received
        // it from old versions, so we filter those.
        Optional<Capabilities> optionalCapabilities = peerManager.findPeersCapabilities(peersNodeAddress);
        if (optionalCapabilities.isPresent()) {
            boolean result = optionalCapabilities.get().containsAll(((CapabilityRequiringPayload) message).getRequiredCapabilities());

            if (!result)
                log.warn("We don't send the message because the peer does not support the required capability. " +
                        "peersNodeAddress={}", peersNodeAddress);

            return !result;
        }

        log.warn("We don't have the peer in our persisted peers so we don't know their capabilities. " +
                "We decide to not sent the msg. peersNodeAddress={}", peersNodeAddress);
        return true;

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
                        public void onSufficientlyBroadcast(List<Broadcaster.BroadcastRequest> broadcastRequests) {
                            broadcastRequests.stream()
                                    .filter(broadcastRequest -> broadcastRequest.getMessage() instanceof AddDataMessage)
                                    .filter(broadcastRequest -> {
                                        AddDataMessage addDataMessage = (AddDataMessage) broadcastRequest.getMessage();
                                        return addDataMessage.getProtectedStorageEntry().equals(protectedMailboxStorageEntry);
                                    })
                                    .forEach(e -> sendMailboxMessageListener.onStoredInMailbox());
                        }

                        @Override
                        public void onNotSufficientlyBroadcast(int numOfCompletedBroadcasts, int numOfFailedBroadcast) {
                            sendMailboxMessageListener.onFault("Message was not sufficiently broadcast.\n" +
                                    "numOfCompletedBroadcasts: " + numOfCompletedBroadcasts + ".\n" +
                                    "numOfFailedBroadcast=" + numOfFailedBroadcast);
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
                    log.error("Signing at getMailboxDataWithSignedSeqNr failed.");
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
            ProtectedMailboxStorageEntry mailboxData = mailboxMap.get(uid).first;
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
}
