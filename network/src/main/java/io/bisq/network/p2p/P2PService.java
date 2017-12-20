package io.bisq.network.p2p;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.KeyRing;
import io.bisq.common.crypto.PubKeyRing;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.persistable.PersistedDataHost;
import io.bisq.common.util.Utilities;
import io.bisq.network.Socks5ProxyProvider;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.messaging.DecryptedMailboxListener;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.BroadcastHandler;
import io.bisq.network.p2p.peers.Broadcaster;
import io.bisq.network.p2p.peers.PeerManager;
import io.bisq.network.p2p.peers.getdata.RequestDataManager;
import io.bisq.network.p2p.peers.keepalive.KeepAliveManager;
import io.bisq.network.p2p.peers.peerexchange.PeerExchangeManager;
import io.bisq.network.p2p.seed.SeedNodesRepository;
import io.bisq.network.p2p.storage.HashMapChangedListener;
import io.bisq.network.p2p.storage.P2PDataStorage;
import io.bisq.network.p2p.storage.messages.AddDataMessage;
import io.bisq.network.p2p.storage.messages.BroadcastMessage;
import io.bisq.network.p2p.storage.messages.RefreshOfferMessage;
import io.bisq.network.p2p.storage.payload.*;
import javafx.beans.property.*;
import lombok.Getter;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class P2PService implements SetupListener, MessageListener, ConnectionListener, RequestDataManager.Listener,
        HashMapChangedListener, PersistedDataHost {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);
    public static final int MAX_CONNECTIONS_DEFAULT = 12;

    private final SeedNodesRepository seedNodesRepository;
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
                      SeedNodesRepository seedNodesRepository,
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
        this.seedNodesRepository = seedNodesRepository;
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
        Log.traceCall();
        if (listener != null)
            addP2PServiceListener(listener);

        networkNode.start(this);
    }

    public void readFromResources(String resourceFileName) {
        p2PDataStorage.readFromResources(resourceFileName);
    }

    public void onAllServicesInitialized() {
        Log.traceCall();
        if (networkNode.getNodeAddress() != null) {
            p2PDataStorage.getMap().values().stream().forEach(protectedStorageEntry -> {
                if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                    processProtectedMailboxStorageEntry((ProtectedMailboxStorageEntry) protectedStorageEntry);
            });
        } else {
            // If our HS is still not published
            networkNode.nodeAddressProperty().addListener((observable, oldValue, newValue) -> {
                if (newValue != null) {
                    p2PDataStorage.getMap().values().stream().forEach(protectedStorageEntry -> {
                        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                            processProtectedMailboxStorageEntry((ProtectedMailboxStorageEntry) protectedStorageEntry);
                    });
                }
            });
        }
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        Log.traceCall();
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
        Log.traceCall();

        socks5ProxyProvider.setSocks5ProxyInternal(networkNode.getSocksProxy());

        boolean seedNodesAvailable = requestDataManager.requestPreliminaryData();

        keepAliveManager.start();
        p2pServiceListeners.stream().forEach(SetupListener::onTorNodeReady);

        if (!seedNodesAvailable) {
            isBootstrapped = true;
            p2pServiceListeners.stream().forEach(P2PServiceListener::onNoSeedNodeAvailable);
        }
    }

    @Override
    public void onHiddenServicePublished() {
        Log.traceCall();

        checkArgument(networkNode.getNodeAddress() != null, "Address must be set when we have the hidden service ready");

        hiddenServicePublished.set(true);

        p2pServiceListeners.stream().forEach(SetupListener::onHiddenServicePublished);
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onSetupFailed(throwable));
    }

    @Override
    public void onRequestCustomBridges() {
        p2pServiceListeners.stream().forEach(SetupListener::onRequestCustomBridges);
    }

    // Called from networkReadyBinding
    private void onNetworkReady() {
        Log.traceCall();
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
            p2pServiceListeners.stream().forEach(P2PServiceListener::onBootstrapComplete);
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
        p2pServiceListeners.stream().forEach(P2PServiceListener::onRequestingDataCompleted);
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
        Log.traceCall();
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
    public void onMessage(NetworkEnvelope networkEnvelop, Connection connection) {
        if (networkEnvelop instanceof PrefixedSealedAndSignedMessage) {
            Log.traceCall("\n\t" + networkEnvelop.toString() + "\n\tconnection=" + connection);
            // Seed nodes don't have set the encryptionService
            try {
                PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = (PrefixedSealedAndSignedMessage) networkEnvelop;
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
                        decryptedDirectMessageListeners.stream().forEach(
                                e -> e.onDirectMessage(decryptedMessageWithPubKey, connection.getPeersNodeAddressOptional().get()));
                    else
                        log.error("peersNodeAddress is not available at onMessage.");
                } else {
                    log.debug("Wrong receiverAddressMaskHash. The message is not intended for us.");
                }
            } catch (CryptoException e) {
                log.debug(networkEnvelop.toString());
                log.debug(e.toString());
                log.debug("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                        "That is expected if the message is not intended for us.");
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedStorageEntry protectedStorageEntry) {
        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
            processProtectedMailboxStorageEntry((ProtectedMailboxStorageEntry) protectedStorageEntry);
    }

    @Override
    public void onRemoved(ProtectedStorageEntry data) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DirectMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedDirectMessage(NodeAddress peerNodeAddress, PubKeyRing pubKeyRing, NetworkEnvelope message,
                                           SendDirectMessageListener sendDirectMessageListener) {
        Log.traceCall();
        checkNotNull(peerNodeAddress, "PeerAddress must not be null (sendEncryptedDirectMessage)");
        if (isBootstrapped()) {
            doSendEncryptedDirectMessage(peerNodeAddress, pubKeyRing, message, sendDirectMessageListener);
        } else {
            throw new NetworkNotReadyException();
        }
    }

    private void doSendEncryptedDirectMessage(@NotNull NodeAddress peersNodeAddress, PubKeyRing pubKeyRing, NetworkEnvelope message,
                                              SendDirectMessageListener sendDirectMessageListener) {
        Log.traceCall();
        checkNotNull(peersNodeAddress, "Peer node address must not be null at doSendEncryptedDirectMessage");
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at doSendEncryptedDirectMessage");
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
                    sendDirectMessageListener.onFault();
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error(message.toString());
            log.error(e.toString());
            sendDirectMessageListener.onFault();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MailboxMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processProtectedMailboxStorageEntry(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        Log.traceCall();
        final NodeAddress nodeAddress = networkNode.getNodeAddress();
        // Seed nodes don't receive mailbox network_messages
        if (nodeAddress != null && !seedNodesRepository.isSeedNode(nodeAddress)) {
            Log.traceCall();
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
                        log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress="
                                + senderNodeAddress + " / my address=" + getAddress());
                        decryptedMailboxListeners.stream().forEach(
                                e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, senderNodeAddress));
                    } else {
                        log.warn("tryDecryptMailboxData: Expected MailboxMessage but got other type. " +
                                "decryptedMsgWithPubKey.message=", decryptedMessageWithPubKey.getNetworkEnvelope());
                    }
                } catch (CryptoException e) {
                    log.debug(e.toString());
                    log.debug("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                            "That is expected if the message is not intended for us.");
                }
            } else {
                log.debug("Wrong blurredAddressHash. The message is not intended for us.");
            }
        }
    }

    public void sendEncryptedMailboxMessage(NodeAddress peersNodeAddress, PubKeyRing peersPubKeyRing,
                                            NetworkEnvelope message,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall("message " + message);
        checkNotNull(peersNodeAddress,
                "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing),
                "We got own keyring instead of that from peer");

        if (isBootstrapped()) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    log.debug("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                            "Encrypt message:\nmessage={}"
                            + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);
                    PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                            networkNode.getNodeAddress(),
                            encryptionService.encryptAndSign(peersPubKeyRing, message),
                            peersNodeAddress.getAddressPrefixHash(),
                            UUID.randomUUID().toString());
                    SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, prefixedSealedAndSignedMessage);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(@Nullable Connection connection) {
                            log.trace("SendEncryptedMailboxMessage onSuccess");
                            sendMailboxMessageListener.onArrived();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.info("We cannot send message to peer. Peer might be offline. We will store message in mailbox. peersNodeAddress=" + peersNodeAddress);
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
            } else {
                sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                        "Please check your internet connection.");
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }


    private void addMailboxData(MailboxStoragePayload expirableMailboxStoragePayload,
                                PublicKey receiversPublicKey,
                                SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall();

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
                    boolean result = p2PDataStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, networkNode.getNodeAddress(), listener, true);
                    if (!result) {
                        //TODO remove and add again with a delay to ensure the data will be broadcasted
                        // The p2PDataStorage.remove makes probably sense but need to be analysed more.
                        // Don't change that if it is not 100% clear.
                        sendMailboxMessageListener.onFault("Data already exists in our local database");
                        boolean removeResult = p2PDataStorage.remove(protectedMailboxStorageEntry, networkNode.getNodeAddress(), true);
                        log.debug("remove result=" + removeResult);
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
        UserThread.runAfter(() -> delayedRemoveEntryFromMailbox(decryptedMessageWithPubKey), 2);
    }

    private void delayedRemoveEntryFromMailbox(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        Log.traceCall();
        if (isBootstrapped()) {
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
                        p2PDataStorage.removeMailboxData(protectedMailboxStorageEntry, networkNode.getNodeAddress(), true);
                    } catch (CryptoException e) {
                        log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                    }

                    mailboxMap.remove(uid);
                    log.info("Removed successfully decryptedMsgWithPubKey. uid={}", uid);
                }
            } else {
                log.warn("uid for mailbox entry not found in mailboxMap." + "uid={}", uid);
            }
        } else {
            throw new NetworkNotReadyException();
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addPersistableNetworkPayload(PersistableNetworkPayload payload, boolean reBroadcast) {
        return p2PDataStorage.addPersistableNetworkPayload(payload, networkNode.getNodeAddress(), true, true, reBroadcast, false);
    }

    public boolean addProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload, boolean isDataOwner) {
        Log.traceCall();
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedStorageEntry(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.addProtectedStorageEntry(protectedStorageEntry, networkNode.getNodeAddress(), null, isDataOwner);
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean refreshTTL(ProtectedStoragePayload protectedStoragePayload, boolean isDataOwner) {
        Log.traceCall();
        if (isBootstrapped()) {
            try {
                RefreshOfferMessage refreshTTLMessage = p2PDataStorage.getRefreshTTLMessage(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.refreshTTL(refreshTTLMessage, networkNode.getNodeAddress(), isDataOwner);
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean removeData(ProtectedStoragePayload protectedStoragePayload, boolean isDataOwner) {
        Log.traceCall();
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedStorageEntry(protectedStoragePayload, keyRing.getSignatureKeyPair());
                return p2PDataStorage.remove(protectedStorageEntry, networkNode.getNodeAddress(), isDataOwner);
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
