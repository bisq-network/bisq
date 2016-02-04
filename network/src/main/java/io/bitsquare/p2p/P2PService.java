package io.bitsquare.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.Log;
import io.bitsquare.app.ProgramArguments;
import io.bitsquare.common.ByteArray;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.crypto.PrefixedSealedAndSignedMessage;
import io.bitsquare.p2p.messaging.*;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.Broadcaster;
import io.bitsquare.p2p.peers.PeerExchangeManager;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.RequestDataManager;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.data.ExpirableMailboxPayload;
import io.bitsquare.p2p.storage.data.ExpirablePayload;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;
import io.bitsquare.p2p.storage.messages.AddDataMessage;
import javafx.beans.property.*;
import javafx.beans.value.ChangeListener;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class P2PService implements SetupListener, MessageListener, ConnectionListener, RequestDataManager.Listener,
        HashMapChangedListener {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);

    private final SeedNodesRepository seedNodesRepository;
    private final int port;
    private final File torDir;
    private final Optional<EncryptionService> optionalEncryptionService;
    private final Optional<KeyRing> optionalKeyRing;

    // set in init
    private NetworkNode networkNode;
    private Broadcaster broadcaster;
    private P2PDataStorage p2PDataStorage;
    private PeerManager peerManager;
    private RequestDataManager requestDataManager;
    private PeerExchangeManager peerExchangeManager;

    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> networkReadyBinding;
    private final Set<DecryptedDirectMessageListener> decryptedDirectMessageListeners = new CopyOnWriteArraySet<>();
    private final Set<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    private final Set<P2PServiceListener> p2pServiceListeners = new CopyOnWriteArraySet<>();
    private final Map<DecryptedMsgWithPubKey, ProtectedMailboxData> mailboxMap = new HashMap<>();
    private final Set<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    private final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty preliminaryDataReceived = new SimpleBooleanProperty();
    private final IntegerProperty numConnectedPeers = new SimpleIntegerProperty(0);

    private volatile boolean shutDownInProgress;
    private boolean shutDownComplete;
    private ChangeListener<NodeAddress> connectionNodeAddressListener;
    private Subscription networkReadySubscription;
    private boolean isBootstrapped;
    private ChangeListener<Number> numOfBroadcastsChangeListener;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called also from SeedNodeP2PService
    @Inject
    public P2PService(SeedNodesRepository seedNodesRepository,
                      @Named(ProgramArguments.PORT_KEY) int port,
                      @Named(ProgramArguments.TOR_DIR) File torDir,
                      @Named(ProgramArguments.USE_LOCALHOST) boolean useLocalhost,
                      @Named(ProgramArguments.NETWORK_ID) int networkId,
                      @Named("storage.dir") File storageDir,
                      @Nullable EncryptionService encryptionService,
                      @Nullable KeyRing keyRing) {
        this.seedNodesRepository = seedNodesRepository;
        this.port = port;
        this.torDir = torDir;

        optionalEncryptionService = encryptionService == null ? Optional.empty() : Optional.of(encryptionService);
        optionalKeyRing = keyRing == null ? Optional.empty() : Optional.of(keyRing);

        init(useLocalhost, networkId, storageDir);
    }

    private void init(boolean useLocalhost, int networkId, File storageDir) {
        connectionNodeAddressListener = (observable, oldValue, newValue) -> {
            UserThread.execute(() -> numConnectedPeers.set(networkNode.getNodeAddressesOfConfirmedConnections().size()));
        };

        networkNode = useLocalhost ? new LocalhostNetworkNode(port) : new TorNetworkNode(port, torDir);
        networkNode.addConnectionListener(this);
        networkNode.addMessageListener(this);

        broadcaster = new Broadcaster(networkNode);

        p2PDataStorage = new P2PDataStorage(broadcaster, networkNode, storageDir);
        p2PDataStorage.addHashMapChangedListener(this);

        Set<NodeAddress> seedNodeAddresses = seedNodesRepository.getSeedNodeAddresses(useLocalhost, networkId);
        peerManager = new PeerManager(networkNode, seedNodeAddresses, storageDir);

        requestDataManager = new RequestDataManager(networkNode, p2PDataStorage, peerManager, seedNodeAddresses, this);

        peerExchangeManager = new PeerExchangeManager(networkNode, peerManager, seedNodeAddresses);

        // We need to have both the initial data delivered and the hidden service published
        networkReadyBinding = EasyBind.combine(hiddenServicePublished, preliminaryDataReceived,
                (hiddenServicePublished, preliminaryDataReceived)
                        -> hiddenServicePublished && preliminaryDataReceived);
        networkReadySubscription = networkReadyBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                onNetworkReady();
        });
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

    public void shutDown(Runnable shutDownCompleteHandler) {
        Log.traceCall();
        if (!shutDownInProgress) {
            shutDownInProgress = true;

            shutDownResultHandlers.add(shutDownCompleteHandler);

            if (p2PDataStorage != null)
                p2PDataStorage.shutDown();

            if (peerManager != null)
                peerManager.shutDown();

            if (requestDataManager != null)
                requestDataManager.shutDown();

            if (peerExchangeManager != null)
                peerExchangeManager.shutDown();

            if (networkNode != null)
                networkNode.shutDown(() -> {
                    shutDownResultHandlers.stream().forEach(Runnable::run);
                    shutDownComplete = true;
                });

            if (networkReadySubscription != null)
                networkReadySubscription.unsubscribe();
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
     * <p>
     * Variant 1 (normal expected mode):
     * onTorNodeReady -> requestDataManager.firstDataRequestFromAnySeedNode()
     * RequestDataManager.Listener.onDataReceived && onHiddenServicePublished -> onNetworkReady()
     * <p>
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

        requestDataManager.requestPreliminaryData();
        p2pServiceListeners.stream().forEach(SetupListener::onTorNodeReady);
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


    // Called from networkReadyBinding
    private void onNetworkReady() {
        Log.traceCall();
        networkReadySubscription.unsubscribe();

        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");

        requestDataManager.requestUpdatesData();
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
        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");
        peerExchangeManager.requestReportedPeersFromSeedNodes(seedNodeOfPreliminaryDataRequest.get());

        isBootstrapped = true;
        p2pServiceListeners.stream().forEach(P2PServiceListener::onBootstrapComplete);
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
        if (connection.getPeersNodeAddressOptional().isPresent()) {
            connectionNodeAddressListener.changed(connection.getNodeAddressProperty(), null,
                    connection.getNodeAddressProperty().get());
        } else {
            connection.getNodeAddressProperty().addListener(connectionNodeAddressListener);
        }
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        Log.traceCall();
        connection.getNodeAddressProperty().removeListener(connectionNodeAddressListener);
        // We removed the listener after a delay to be sure the connection has been removed 
        // from the networkNode already.
        UserThread.runAfter(() ->
                connectionNodeAddressListener.changed(connection.getNodeAddressProperty(), null,
                        connection.getNodeAddressProperty().get())
                , 1);
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof PrefixedSealedAndSignedMessage) {
            Log.traceCall("\n\t" + message.toString() + "\n\tconnection=" + connection);
            // Seed nodes don't have set the encryptionService
            if (optionalEncryptionService.isPresent()) {
                try {
                    PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = (PrefixedSealedAndSignedMessage) message;
                    if (verifyAddressPrefixHash(prefixedSealedAndSignedMessage)) {
                        // We set connectionType to that connection to avoid that is get closed when 
                        // we get too many connection attempts.
                        connection.setPeerType(Connection.PeerType.DIRECT_MSG_PEER);

                        DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                                prefixedSealedAndSignedMessage.sealedAndSigned);

                        log.info("\n\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n" +
                                "Decrypted SealedAndSignedMessage:\ndecryptedMsgWithPubKey={}"
                                + "\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n", decryptedMsgWithPubKey);
                        connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress ->
                                decryptedDirectMessageListeners.stream().forEach(
                                        e -> e.onDirectMessage(decryptedMsgWithPubKey, peersNodeAddress)));
                    } else {
                        log.info("Wrong receiverAddressMaskHash. The message is not intended for us.");
                    }
                } catch (CryptoException e) {
                    log.info("Decryption of SealedAndSignedMessage failed. " +
                            "That is expected if the message is not intended for us.");
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(ProtectedData entry) {
        if (entry instanceof ProtectedMailboxData)
            processProtectedMailboxData((ProtectedMailboxData) entry);
    }

    @Override
    public void onRemoved(ProtectedData entry) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // DirectMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedDirectMessage(NodeAddress peerNodeAddress, PubKeyRing pubKeyRing, DirectMessage message,
                                           SendDirectMessageListener sendDirectMessageListener) {
        Log.traceCall();
        checkNotNull(peerNodeAddress, "PeerAddress must not be null (sendEncryptedDirectMessage)");
        if (isBootstrapped()) {
            doSendEncryptedDirectMessage(peerNodeAddress, pubKeyRing, message, sendDirectMessageListener);
        } else {
            throw new NetworkNotReadyException();
        }
    }

    private void doSendEncryptedDirectMessage(@NotNull NodeAddress peersNodeAddress, PubKeyRing pubKeyRing, DirectMessage message,
                                              SendDirectMessageListener sendDirectMessageListener) {
        Log.traceCall();
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at doSendEncryptedDirectMessage");
        checkArgument(optionalEncryptionService.isPresent(), "EncryptionService not set. Seems that is called on a seed node which must not happen.");
        checkNotNull(networkNode.getNodeAddress(), "networkNode.getNodeAddress() must not be null.");
        try {
            log.info("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                    "Encrypt message:\nmessage={}"
                    + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);
            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(networkNode.getNodeAddress(),
                    optionalEncryptionService.get().encryptAndSign(pubKeyRing, message),
                    peersNodeAddress.getAddressPrefixHash());
            SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendDirectMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    throwable.printStackTrace();
                    sendDirectMessageListener.onFault();
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            sendDirectMessageListener.onFault();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MailboxMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processProtectedMailboxData(ProtectedMailboxData mailboxData) {
        // Seed nodes don't have set the encryptionService
        if (optionalEncryptionService.isPresent()) {
            Log.traceCall();
            ExpirablePayload expirablePayload = mailboxData.expirablePayload;
            if (expirablePayload instanceof ExpirableMailboxPayload) {
                ExpirableMailboxPayload expirableMailboxPayload = (ExpirableMailboxPayload) expirablePayload;
                PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = expirableMailboxPayload.prefixedSealedAndSignedMessage;
                if (verifyAddressPrefixHash(prefixedSealedAndSignedMessage)) {
                    try {
                        DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                                prefixedSealedAndSignedMessage.sealedAndSigned);
                        if (decryptedMsgWithPubKey.message instanceof MailboxMessage) {
                            MailboxMessage mailboxMessage = (MailboxMessage) decryptedMsgWithPubKey.message;
                            NodeAddress senderNodeAddress = mailboxMessage.getSenderNodeAddress();
                            checkNotNull(senderNodeAddress, "senderAddress must not be null for mailbox messages");

                            mailboxMap.put(decryptedMsgWithPubKey, mailboxData);
                            log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress="
                                    + senderNodeAddress + " / my address=" + getAddress());
                            decryptedMailboxListeners.stream().forEach(
                                    e -> e.onMailboxMessageAdded(decryptedMsgWithPubKey, senderNodeAddress));
                        } else {
                            log.warn("tryDecryptMailboxData: Expected MailboxMessage but got other type. " +
                                    "decryptedMsgWithPubKey.message=", decryptedMsgWithPubKey.message);
                        }
                    } catch (CryptoException e) {
                        log.trace("Decryption of SealedAndSignedMessage failed. " +
                                "That is expected if the message is not intended for us. " + e.getMessage());
                    }
                } else {
                    log.info("Wrong blurredAddressHash. The message is not intended for us.");
                }
            }
        }
    }

    public void sendEncryptedMailboxMessage(NodeAddress peersNodeAddress, PubKeyRing peersPubKeyRing,
                                            MailboxMessage message,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall("message " + message);
        checkNotNull(peersNodeAddress,
                "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(optionalKeyRing.isPresent(),
                "keyRing not set. Seems that is called on a seed node which must not happen.");
        checkArgument(!optionalKeyRing.get().getPubKeyRing().equals(peersPubKeyRing),
                "We got own keyring instead of that from peer");
        checkArgument(optionalEncryptionService.isPresent(),
                "EncryptionService not set. Seems that is called on a seed node which must not happen.");

        if (isBootstrapped()) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    log.info("\n\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n" +
                            "Encrypt message:\nmessage={}"
                            + "\nEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE\n", message);
                    PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                            networkNode.getNodeAddress(),
                            optionalEncryptionService.get().encryptAndSign(peersPubKeyRing, message),
                            peersNodeAddress.getAddressPrefixHash());
                    SettableFuture<Connection> future = networkNode.sendMessage(peersNodeAddress, prefixedSealedAndSignedMessage);
                    Futures.addCallback(future, new FutureCallback<Connection>() {
                        @Override
                        public void onSuccess(@Nullable Connection connection) {
                            log.trace("SendEncryptedMailboxMessage onSuccess");
                            sendMailboxMessageListener.onArrived();
                        }

                        @Override
                        public void onFailure(@NotNull Throwable throwable) {
                            log.trace("SendEncryptedMailboxMessage onFailure");
                            log.debug(throwable.toString());
                            log.info("We cannot send message to peer. Peer might be offline. We will store message in mailbox.");
                            log.trace("create MailboxEntry with peerAddress " + peersNodeAddress);
                            PublicKey receiverStoragePublicKey = peersPubKeyRing.getSignaturePubKey();
                            addMailboxData(new ExpirableMailboxPayload(prefixedSealedAndSignedMessage,
                                            optionalKeyRing.get().getSignatureKeyPair().getPublic(),
                                            receiverStoragePublicKey),
                                    receiverStoragePublicKey,
                                    sendMailboxMessageListener);
                        }
                    });
                } catch (CryptoException e) {
                    log.error("sendEncryptedMessage failed");
                    e.printStackTrace();
                    sendMailboxMessageListener.onFault("Data already exist in our local database");
                }
            } else {
                sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                        "Please check your internet connection.");
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }


    private void addMailboxData(ExpirableMailboxPayload expirableMailboxPayload,
                                PublicKey receiversPublicKey,
                                SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(),
                "keyRing not set. Seems that is called on a seed node which must not happen.");

        if (isBootstrapped()) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    ProtectedMailboxData protectedMailboxData = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                            expirableMailboxPayload,
                            optionalKeyRing.get().getSignatureKeyPair(),
                            receiversPublicKey);

                    Timer sendMailboxMessageTimeoutTimer = UserThread.runAfter(() -> {
                        boolean result = p2PDataStorage.remove(protectedMailboxData, networkNode.getNodeAddress());
                        log.debug("remove result=" + result);
                        sendMailboxMessageListener.onFault("A timeout occurred when trying to broadcast mailbox data.");
                    }, 30);
                    Broadcaster.Listener listener = message -> {
                        if (message instanceof AddDataMessage &&
                                ((AddDataMessage) message).data.equals(protectedMailboxData)) {
                            sendMailboxMessageListener.onStoredInMailbox();
                            sendMailboxMessageTimeoutTimer.cancel();
                        }
                    };
                    broadcaster.addListener(listener);
                    if (numOfBroadcastsChangeListener != null) {
                        log.warn("numOfBroadcastsChangeListener should be null");
                        broadcaster.getNumOfBroadcastsProperty().removeListener(numOfBroadcastsChangeListener);
                    }
                    numOfBroadcastsChangeListener = (observable, oldValue, newValue) -> {
                        // We want to get at least 1 successful broadcast
                        if ((int) newValue > 0)
                            broadcaster.removeListener(listener);

                        UserThread.execute(() -> {
                            broadcaster.getNumOfBroadcastsProperty().removeListener(numOfBroadcastsChangeListener);
                            numOfBroadcastsChangeListener = null;
                        });
                    };
                    broadcaster.getNumOfBroadcastsProperty().addListener(numOfBroadcastsChangeListener);

                    boolean result = p2PDataStorage.add(protectedMailboxData, networkNode.getNodeAddress());
                    if (!result) {
                        sendMailboxMessageTimeoutTimer.cancel();
                        broadcaster.removeListener(listener);
                        broadcaster.getNumOfBroadcastsProperty().removeListener(numOfBroadcastsChangeListener);
                        sendMailboxMessageListener.onFault("Data already exists in our local database");
                        boolean result2 = p2PDataStorage.remove(protectedMailboxData, networkNode.getNodeAddress());
                        log.debug("remove result=" + result2);
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

    public void removeEntryFromMailbox(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            if (mailboxMap.containsKey(decryptedMsgWithPubKey)) {
                ProtectedMailboxData mailboxData = mailboxMap.get(decryptedMsgWithPubKey);
                if (mailboxData != null && mailboxData.expirablePayload instanceof ExpirableMailboxPayload) {
                    ExpirableMailboxPayload expirableMailboxPayload = (ExpirableMailboxPayload) mailboxData.expirablePayload;
                    PublicKey receiversPubKey = mailboxData.receiversPubKey;
                    checkArgument(receiversPubKey.equals(optionalKeyRing.get().getSignatureKeyPair().getPublic()),
                            "receiversPubKey is not matching with our key. That must not happen.");
                    try {
                        ProtectedMailboxData protectedMailboxData = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                                expirableMailboxPayload,
                                optionalKeyRing.get().getSignatureKeyPair(),
                                receiversPubKey);
                        p2PDataStorage.removeMailboxData(protectedMailboxData, networkNode.getNodeAddress());
                    } catch (CryptoException e) {
                        log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                    }

                    mailboxMap.remove(decryptedMsgWithPubKey);
                    log.trace("Removed successfully decryptedMsgWithPubKey.");
                }
            } else {
                log.warn("decryptedMsgWithPubKey not found in mailboxMap. That should never happen." +
                        "\n\tdecryptedMsgWithPubKey={}\n\tmailboxMap={}", decryptedMsgWithPubKey, mailboxMap);
            }
        } else {
            throw new NetworkNotReadyException();
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        return doAddData(expirablePayload, false);
    }

    public boolean republishData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        return doAddData(expirablePayload, true);
    }

    private boolean doAddData(ExpirablePayload expirablePayload, boolean rePublish) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            try {
                ProtectedData protectedData = p2PDataStorage.getDataWithSignedSeqNr(expirablePayload, optionalKeyRing.get().getSignatureKeyPair());
                if (rePublish)
                    return p2PDataStorage.rePublish(protectedData, networkNode.getNodeAddress());
                else
                    return p2PDataStorage.add(protectedData, networkNode.getNodeAddress());
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean removeData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            try {
                ProtectedData protectedData = p2PDataStorage.getDataWithSignedSeqNr(expirablePayload, optionalKeyRing.get().getSignatureKeyPair());
                return p2PDataStorage.remove(protectedData, networkNode.getNodeAddress());
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

    public void removeDecryptedMailListener(DecryptedDirectMessageListener listener) {
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

    public Set<NodeAddress> getNodeAddressesOfConnectedPeers() {
        return networkNode.getNodeAddressesOfConfirmedConnections();
    }

    public ReadOnlyIntegerProperty getNumConnectedPeers() {
        return numConnectedPeers;
    }

    public Map<ByteArray, ProtectedData> getDataMap() {
        return p2PDataStorage.getMap();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean verifyAddressPrefixHash(PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage) {
        if (networkNode.getNodeAddress() != null) {
            byte[] blurredAddressHash = networkNode.getNodeAddress().getAddressPrefixHash();
            return blurredAddressHash != null &&
                    Arrays.equals(blurredAddressHash, prefixedSealedAndSignedMessage.addressPrefixHash);
        } else {
            log.debug("myOnionAddress is null at verifyAddressPrefixHash. That is expected at startup.");
            return false;
        }
    }
}
