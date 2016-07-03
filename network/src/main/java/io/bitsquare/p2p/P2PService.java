package io.bitsquare.p2p;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.Log;
import io.bitsquare.common.Clock;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.crypto.DecryptedMsgWithPubKey;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.network.OptionKeys;
import io.bitsquare.p2p.messaging.*;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.BroadcastHandler;
import io.bitsquare.p2p.peers.Broadcaster;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.getdata.RequestDataManager;
import io.bitsquare.p2p.peers.keepalive.KeepAliveManager;
import io.bitsquare.p2p.peers.peerexchange.PeerExchangeManager;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.messages.AddDataMessage;
import io.bitsquare.p2p.storage.messages.BroadcastMessage;
import io.bitsquare.p2p.storage.messages.RefreshTTLMessage;
import io.bitsquare.p2p.storage.payload.MailboxStoragePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedMailboxStorageEntry;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.storage.FileUtil;
import io.bitsquare.storage.Storage;
import javafx.beans.property.*;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.Subscription;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Paths;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

public class P2PService implements SetupListener, MessageListener, ConnectionListener, RequestDataManager.Listener,
        HashMapChangedListener {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);
    public static final int MAX_CONNECTIONS_DEFAULT = 12;

    private final SeedNodesRepository seedNodesRepository;
    private final int port;
    private final int maxConnections;
    private final File torDir;
    private Clock clock;
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
    private final Map<String, ProtectedMailboxStorageEntry> mailboxMap = new HashMap<>();
    private final Set<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    private final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty preliminaryDataReceived = new SimpleBooleanProperty();
    private final IntegerProperty numConnectedPeers = new SimpleIntegerProperty(0);

    private volatile boolean shutDownInProgress;
    private boolean shutDownComplete;
    private Subscription networkReadySubscription;
    private boolean isBootstrapped;
    private KeepAliveManager keepAliveManager;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    // Called also from SeedNodeP2PService
    @Inject
    public P2PService(SeedNodesRepository seedNodesRepository,
                      @Named(OptionKeys.PORT_KEY) int port,
                      @Named(OptionKeys.TOR_DIR) File torDir,
                      @Named(OptionKeys.USE_LOCALHOST) boolean useLocalhost,
                      @Named(OptionKeys.NETWORK_ID) int networkId,
                      @Named(OptionKeys.MAX_CONNECTIONS) int maxConnections,
                      @Named(Storage.DIR_KEY) File storageDir,
                      @Named(OptionKeys.SEED_NODES_KEY) String seedNodes,
                      Clock clock,
                      @Nullable EncryptionService encryptionService,
                      @Nullable KeyRing keyRing) {
        this(
                seedNodesRepository,
                port,
                maxConnections,
                torDir,
                useLocalhost,
                networkId,
                storageDir,
                seedNodes,
                clock,
                encryptionService,
                keyRing
        );
    }

    @VisibleForTesting
    public P2PService(SeedNodesRepository seedNodesRepository,
                      int port, int maxConnections,
                      File torDir,
                      boolean useLocalhost,
                      int networkId,
                      File storageDir,
                      String seedNodes,
                      Clock clock,
                      @Nullable EncryptionService encryptionService,
                      @Nullable KeyRing keyRing) {
        this.seedNodesRepository = seedNodesRepository;
        this.port = port;
        this.maxConnections = maxConnections;
        this.torDir = torDir;
        this.clock = clock;

        optionalEncryptionService = Optional.ofNullable(encryptionService);
        optionalKeyRing = Optional.ofNullable(keyRing);

        init(useLocalhost, networkId, storageDir, seedNodes);
    }

    private void init(boolean useLocalhost, int networkId, File storageDir, String seedNodes) {
        if (!useLocalhost)
            FileUtil.rollingBackup(new File(Paths.get(torDir.getAbsolutePath(), "hiddenservice").toString()), "private_key");

        networkNode = useLocalhost ? new LocalhostNetworkNode(port) : new TorNetworkNode(port, torDir);
        networkNode.addConnectionListener(this);
        networkNode.addMessageListener(this);

        Set<NodeAddress> seedNodeAddresses;
        if (seedNodes != null && !seedNodes.isEmpty())
            seedNodeAddresses = Arrays.asList(seedNodes.replace(" ", "").split(",")).stream().map(NodeAddress::new).collect(Collectors.toSet());
        else
            seedNodeAddresses = seedNodesRepository.getSeedNodeAddresses(useLocalhost, networkId);

        peerManager = new PeerManager(networkNode, maxConnections, seedNodeAddresses, storageDir, clock);

        broadcaster = new Broadcaster(networkNode, peerManager);

        p2PDataStorage = new P2PDataStorage(broadcaster, networkNode, storageDir);
        p2PDataStorage.addHashMapChangedListener(this);

        requestDataManager = new RequestDataManager(networkNode, p2PDataStorage, peerManager, seedNodeAddresses, this);

        peerExchangeManager = new PeerExchangeManager(networkNode, peerManager, seedNodeAddresses);

        keepAliveManager = new KeepAliveManager(networkNode, peerManager);


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

    public void start(boolean useBridges, @Nullable P2PServiceListener listener) {
        Log.traceCall();
        if (listener != null)
            addP2PServiceListener(listener);

        networkNode.start(useBridges, this);
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
        keepAliveManager.restart();
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

    @Override
    public void onUseDefaultBridges() {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onUseDefaultBridges());
    }

    @Override
    public void onRequestCustomBridges(Runnable resultHandler) {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onRequestCustomBridges(resultHandler));
    }


    // Called from networkReadyBinding
    private void onNetworkReady() {
        Log.traceCall();
        networkReadySubscription.unsubscribe();

        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeAddressOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");

        requestDataManager.requestUpdateData();
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
        Optional<NodeAddress> seedNodeOfPreliminaryDataRequest = requestDataManager.getNodeAddressOfPreliminaryDataRequest();
        checkArgument(seedNodeOfPreliminaryDataRequest.isPresent(),
                "seedNodeOfPreliminaryDataRequest must be present");
        peerExchangeManager.requestReportedPeersFromSeedNodes(seedNodeOfPreliminaryDataRequest.get());

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

                        log.debug("Try to decrypt...");
                        DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                                prefixedSealedAndSignedMessage.sealedAndSigned);

                        log.info("\n\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n" +
                                "Decrypted SealedAndSignedMessage:\ndecryptedMsgWithPubKey={}"
                                + "\nDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD\n", decryptedMsgWithPubKey);
                        if (connection.getPeersNodeAddressOptional().isPresent())
                            decryptedDirectMessageListeners.stream().forEach(
                                    e -> e.onDirectMessage(decryptedMsgWithPubKey, connection.getPeersNodeAddressOptional().get()));
                        else
                            log.error("peersNodeAddress is not available at onMessage.");
                    } else {
                        log.info("Wrong receiverAddressMaskHash. The message is not intended for us.");
                    }
                } catch (CryptoException e) {
                    log.info(message.toString());
                    log.info(e.toString());
                    log.info("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                            "That is expected if the message is not intended for us.");
                }
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
        checkNotNull(peersNodeAddress, "Peer node address must not be null at doSendEncryptedDirectMessage");
        checkArgument(optionalEncryptionService.isPresent(), "EncryptionService not set. Seems that is called on a seed node which must not happen.");
        checkNotNull(networkNode.getNodeAddress(), "My node address must not be null at doSendEncryptedDirectMessage");
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
            log.error(message.toString());
            log.error(e.toString());
            sendDirectMessageListener.onFault();
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MailboxMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void processProtectedMailboxStorageEntry(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        // Seed nodes don't have set the encryptionService
        if (optionalEncryptionService.isPresent()) {
            Log.traceCall();
            MailboxStoragePayload mailboxStoragePayload = protectedMailboxStorageEntry.getMailboxStoragePayload();
            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = mailboxStoragePayload.prefixedSealedAndSignedMessage;
            if (verifyAddressPrefixHash(prefixedSealedAndSignedMessage)) {
                try {
                    DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                            prefixedSealedAndSignedMessage.sealedAndSigned);
                    if (decryptedMsgWithPubKey.message instanceof MailboxMessage) {
                        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMsgWithPubKey.message;
                        NodeAddress senderNodeAddress = mailboxMessage.getSenderNodeAddress();
                        checkNotNull(senderNodeAddress, "senderAddress must not be null for mailbox messages");

                        mailboxMap.put(mailboxMessage.getUID(), protectedMailboxStorageEntry);
                        log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress="
                                + senderNodeAddress + " / my address=" + getAddress());
                        decryptedMailboxListeners.stream().forEach(
                                e -> e.onMailboxMessageAdded(decryptedMsgWithPubKey, senderNodeAddress));
                    } else {
                        log.warn("tryDecryptMailboxData: Expected MailboxMessage but got other type. " +
                                "decryptedMsgWithPubKey.message=", decryptedMsgWithPubKey.message);
                    }
                } catch (CryptoException e) {
                    log.info(e.toString());
                    log.info("Decryption of prefixedSealedAndSignedMessage.sealedAndSigned failed. " +
                            "That is expected if the message is not intended for us.");
                }
            } else {
                log.info("Wrong blurredAddressHash. The message is not intended for us.");
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
                            addMailboxData(new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                                            optionalKeyRing.get().getSignatureKeyPair().getPublic(),
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
        checkArgument(optionalKeyRing.isPresent(),
                "keyRing not set. Seems that is called on a seed node which must not happen.");

        if (isBootstrapped()) {
            if (!networkNode.getAllConnections().isEmpty()) {
                try {
                    ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                            expirableMailboxStoragePayload,
                            optionalKeyRing.get().getSignatureKeyPair(),
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
                                    ((AddDataMessage) message).protectedStorageEntry.equals(protectedMailboxStorageEntry)) {
                                sendMailboxMessageListener.onStoredInMailbox();
                            }
                        }

                        @Override
                        public void onBroadcastCompleted(BroadcastMessage message, int numOfCompletedBroadcasts, int numOfFailedBroadcasts) {
                            if (numOfCompletedBroadcasts == 0)
                                sendMailboxMessageListener.onFault("Broadcast completed without any successful broadcast");
                        }

                        @Override
                        public void onBroadcastFailed(String errorMessage) {
                            // TODO investigate why not sending sendMailboxMessageListener.onFault. Related probably 
                            // to the logic from BroadcastHandler.sendToPeer
                        }
                    };
                    boolean result = p2PDataStorage.add(protectedMailboxStorageEntry, networkNode.getNodeAddress(), listener, true);
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

    public void removeEntryFromMailbox(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            MailboxMessage mailboxMessage = (MailboxMessage) decryptedMsgWithPubKey.message;
            String uid = mailboxMessage.getUID();
            if (mailboxMap.containsKey(uid)) {
                ProtectedMailboxStorageEntry mailboxData = mailboxMap.get(uid);
                if (mailboxData != null && mailboxData.getStoragePayload() instanceof MailboxStoragePayload) {
                    MailboxStoragePayload expirableMailboxStoragePayload = (MailboxStoragePayload) mailboxData.getStoragePayload();
                    PublicKey receiversPubKey = mailboxData.receiversPubKey;
                    checkArgument(receiversPubKey.equals(optionalKeyRing.get().getSignatureKeyPair().getPublic()),
                            "receiversPubKey is not matching with our key. That must not happen.");
                    try {
                        ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                                expirableMailboxStoragePayload,
                                optionalKeyRing.get().getSignatureKeyPair(),
                                receiversPubKey);
                        p2PDataStorage.removeMailboxData(protectedMailboxStorageEntry, networkNode.getNodeAddress(), true);
                    } catch (CryptoException e) {
                        log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                    }

                    mailboxMap.remove(uid);
                    log.trace("Removed successfully decryptedMsgWithPubKey.");
                }
            } else {
                log.warn("uid for mailbox entry not found in mailboxMap. That should never happen." +
                        "\n\tuid={}\n\tmailboxMap={}\n\tmailboxMessage={}", uid, mailboxMap, mailboxMessage);
            }
        } else {
            throw new NetworkNotReadyException();
        }

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addData(StoragePayload storagePayload, boolean isDataOwner) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedData(storagePayload, optionalKeyRing.get().getSignatureKeyPair());
                return p2PDataStorage.add(protectedStorageEntry, networkNode.getNodeAddress(), null, isDataOwner);
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean refreshTTL(StoragePayload storagePayload, boolean isDataOwner) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            try {
                RefreshTTLMessage refreshTTLMessage = p2PDataStorage.getRefreshTTLMessage(storagePayload, optionalKeyRing.get().getSignatureKeyPair());
                return p2PDataStorage.refreshTTL(refreshTTLMessage, networkNode.getNodeAddress(), isDataOwner);
            } catch (CryptoException e) {
                log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                return false;
            }
        } else {
            throw new NetworkNotReadyException();
        }
    }

    public boolean removeData(StoragePayload storagePayload, boolean isDataOwner) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        if (isBootstrapped()) {
            try {
                ProtectedStorageEntry protectedStorageEntry = p2PDataStorage.getProtectedData(storagePayload, optionalKeyRing.get().getSignatureKeyPair());
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
    @Nullable
    public KeyRing getKeyRing() {
        return optionalKeyRing.isPresent() ? optionalKeyRing.get() : null;
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
