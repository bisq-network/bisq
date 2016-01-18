package io.bitsquare.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.Log;
import io.bitsquare.app.ProgramArguments;
import io.bitsquare.common.ByteArray;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.messaging.*;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.AuthenticationListener;
import io.bitsquare.p2p.peers.PeerManager;
import io.bitsquare.p2p.peers.RequestDataManager;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.P2PDataStorage;
import io.bitsquare.p2p.storage.data.ExpirableMailboxPayload;
import io.bitsquare.p2p.storage.data.ExpirablePayload;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;
import io.bitsquare.storage.Storage;
import javafx.beans.property.*;
import org.fxmisc.easybind.EasyBind;
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

public class P2PService implements SetupListener, MessageListener, ConnectionListener, HashMapChangedListener, AuthenticationListener {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);

    private final SeedNodesRepository seedNodesRepository;
    private final int port;
    private final File torDir;
    private final boolean useLocalhost;
    protected final File storageDir;
    private final Optional<EncryptionService> optionalEncryptionService;
    private final Optional<KeyRing> optionalKeyRing;

    // set in init
    protected NetworkNode networkNode;
    protected PeerManager peerManager;
    protected P2PDataStorage dataStorage;

    private final CopyOnWriteArraySet<DecryptedMailListener> decryptedMailListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    protected final CopyOnWriteArraySet<P2PServiceListener> p2pServiceListeners = new CopyOnWriteArraySet<>();
    private final Map<DecryptedMsgWithPubKey, ProtectedMailboxData> mailboxMap = new HashMap<>();
    private final Set<Address> authenticatedPeerAddresses = new HashSet<>();
    private final CopyOnWriteArraySet<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    protected final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty requestingDataCompleted = new SimpleBooleanProperty();
    protected final BooleanProperty notAuthenticated = new SimpleBooleanProperty(true);
    private final IntegerProperty numAuthenticatedPeers = new SimpleIntegerProperty(0);

    private Address seedNodeOfInitialDataRequest;
    private volatile boolean shutDownInProgress;
    private boolean shutDownComplete;
    @SuppressWarnings("FieldCanBeLocal")
    private MonadicBinding<Boolean> readyForAuthenticationBinding;
    private final Storage<Address> dbStorage;
    private Address myOnionAddress;
    protected RequestDataManager requestDataManager;
    protected Set<Address> seedNodeAddresses;


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
        this.useLocalhost = useLocalhost;
        this.storageDir = storageDir;

        optionalEncryptionService = encryptionService == null ? Optional.empty() : Optional.of(encryptionService);
        optionalKeyRing = keyRing == null ? Optional.empty() : Optional.of(keyRing);

        dbStorage = new Storage<>(storageDir);

        init(networkId, storageDir);
    }

    private void init(int networkId, File storageDir) {
        Log.traceCall();

        // lets check if we have already stored our onion address
        Address persistedOnionAddress = dbStorage.initAndGetPersisted("myOnionAddress");
        if (persistedOnionAddress != null)
            this.myOnionAddress = persistedOnionAddress;

        seedNodeAddresses = seedNodesRepository.getSeedNodeAddresses(useLocalhost, networkId);

        // network node
        networkNode = useLocalhost ? new LocalhostNetworkNode(port) : new TorNetworkNode(port, torDir);
        networkNode.addConnectionListener(this);
        networkNode.addMessageListener(this);

        // peer group 
        peerManager = getNewPeerManager();
        peerManager.setSeedNodeAddresses(seedNodeAddresses);
        peerManager.addAuthenticationListener(this);

        // P2P network data storage 
        dataStorage = new P2PDataStorage(peerManager, networkNode, storageDir);
        dataStorage.addHashMapChangedListener(this);

        // Request data manager
        requestDataManager = getNewRequestDataManager();
        requestDataManager.setRequestDataManagerListener(new RequestDataManager.Listener() {
            @Override
            public void onNoSeedNodeAvailable() {
                p2pServiceListeners.stream().forEach(e -> e.onNoSeedNodeAvailable());
            }

            @Override
            public void onNoPeersAvailable() {
                p2pServiceListeners.stream().forEach(e -> e.onNoPeersAvailable());
            }

            @Override
            public void onDataReceived(Address address) {
                if (!requestingDataCompleted.get()) {
                    seedNodeOfInitialDataRequest = address;
                    requestingDataCompleted.set(true);
                }
                p2pServiceListeners.stream().forEach(e -> e.onRequestingDataCompleted());
            }
        });
        peerManager.addAuthenticationListener(requestDataManager);

        // Test multiple states to check when we are ready for authenticateSeedNode
        // We need to have both the initial data delivered and the hidden service published before we 
        // authenticate to a seed node. 
        readyForAuthenticationBinding = getNewReadyForAuthenticationBinding();
        readyForAuthenticationBinding.subscribe((observable, oldValue, newValue) -> {
            if (newValue)
                authenticateToSeedNode();
        });
    }

    protected MonadicBinding<Boolean> getNewReadyForAuthenticationBinding() {
        return EasyBind.combine(hiddenServicePublished, requestingDataCompleted, notAuthenticated,
                (hiddenServicePublished, requestingDataCompleted, notAuthenticated)
                        -> hiddenServicePublished && requestingDataCompleted && notAuthenticated);
    }

    protected PeerManager getNewPeerManager() {
        return new PeerManager(networkNode, storageDir);
    }

    protected RequestDataManager getNewRequestDataManager() {
        return new RequestDataManager(networkNode, dataStorage, peerManager);
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

            if (dataStorage != null)
                dataStorage.shutDown();

            if (peerManager != null)
                peerManager.shutDown();

            if (requestDataManager != null)
                requestDataManager.shutDown();

            if (networkNode != null)
                networkNode.shutDown(() -> {
                    shutDownResultHandlers.stream().forEach(e -> e.run());
                    shutDownComplete = true;
                });
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
     * onTorNodeReady -> requestDataManager.requestData()
     * RequestDataManager.Listener.onDataReceived && onHiddenServicePublished -> authenticateSeedNode()
     * RequestDataManager.onPeerAddressAuthenticated -> RequestDataManager.requestDataFromAuthenticatedSeedNode()
     * <p>
     * Variant 2 (no seed node available):
     * onTorNodeReady -> requestDataManager.requestData
     * RequestDataManager.Listener.onNoSeedNodeAvailable && onHiddenServicePublished -> retry after 20-30 until
     * seed node is available and data can be retrieved
     * RequestDataManager.Listener.onDataReceived && onHiddenServicePublished -> authenticateSeedNode()
     * RequestDataManager.onPeerAddressAuthenticated -> RequestDataManager.requestDataFromAuthenticatedSeedNode()
     */

    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public void onTorNodeReady() {
        Log.traceCall();
        requestDataManager.requestDataFromSeedNodes(seedNodeAddresses);
        p2pServiceListeners.stream().forEach(e -> e.onTorNodeReady());
    }

    @Override
    public void onHiddenServicePublished() {
        Log.traceCall();
        checkArgument(networkNode.getAddress() != null, "Address must be set when we have the hidden service ready");
        if (myOnionAddress != null) {
            checkArgument(networkNode.getAddress().equals(myOnionAddress),
                    "If we are a seed node networkNode.getAddress() must be same as myOnionAddress.");
        } else {
            myOnionAddress = networkNode.getAddress();
            dbStorage.queueUpForSave(myOnionAddress);
        }

        hiddenServicePublished.set(true);

        p2pServiceListeners.stream().forEach(e -> e.onHiddenServicePublished());
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onSetupFailed(throwable));
    }

    protected void authenticateToSeedNode() {
        Log.traceCall();
        checkNotNull(seedNodeOfInitialDataRequest != null, "seedNodeOfInitialDataRequest must not be null");
        peerManager.authenticateToSeedNode(seedNodeOfInitialDataRequest);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Log.traceCall();
        connection.getPeerAddressOptional().ifPresent(peerAddresses -> authenticatedPeerAddresses.remove(peerAddresses));
        numAuthenticatedPeers.set(authenticatedPeerAddresses.size());
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // AuthenticationListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onPeerAuthenticated(Address peerAddress, Connection connection) {
        Log.traceCall();
        authenticatedPeerAddresses.add(peerAddress);

        if (notAuthenticated.get()) {
            notAuthenticated.set(false);
            p2pServiceListeners.stream().forEach(e -> e.onFirstPeerAuthenticated());
        }

        numAuthenticatedPeers.set(authenticatedPeerAddresses.size());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof SealedAndSignedMessage) {
            Log.traceCall(message.toString());
            // Seed nodes don't have set the encryptionService
            if (optionalEncryptionService.isPresent()) {
                try {
                    SealedAndSignedMessage sealedAndSignedMessage = (SealedAndSignedMessage) message;
                    if (verifyAddressPrefixHash(sealedAndSignedMessage)) {
                        DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                                sealedAndSignedMessage.sealedAndSigned);

                        // We set connectionType to that connection to avoid that is get closed when 
                        // we get too many connection attempts.
                        // That is used as protection against eclipse attacks.
                        connection.setConnectionPriority(ConnectionPriority.DIRECT_MSG);

                        log.info("Received SealedAndSignedMessage and decrypted it: " + decryptedMsgWithPubKey);
                        connection.getPeerAddressOptional().ifPresent(peerAddresses ->
                                decryptedMailListeners.stream().forEach(
                                        e -> e.onMailMessage(decryptedMsgWithPubKey, peerAddresses)));
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
    // MailMessages
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message,
                                         SendMailMessageListener sendMailMessageListener) {
        Log.traceCall();
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailMessage)");
        try {
            checkAuthentication();
            if (!authenticatedPeerAddresses.contains(peerAddress))
                peerManager.authenticateToDirectMessagePeer(peerAddress,
                        () -> doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener),
                        () -> sendMailMessageListener.onFault());
            else
                doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener);
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private void doSendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message,
                                            SendMailMessageListener sendMailMessageListener) {
        Log.traceCall();
        checkArgument(optionalEncryptionService.isPresent(), "EncryptionService not set. Seems that is called on a seed node which must not happen.");
        try {
            SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(
                    optionalEncryptionService.get().encryptAndSign(pubKeyRing, message), peerAddress.getAddressPrefixHash());
            SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, sealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendMailMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    throwable.printStackTrace();
                    sendMailMessageListener.onFault();
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            sendMailMessageListener.onFault();
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
                SealedAndSignedMessage sealedAndSignedMessage = expirableMailboxPayload.sealedAndSignedMessage;
                if (verifyAddressPrefixHash(sealedAndSignedMessage)) {
                    try {
                        DecryptedMsgWithPubKey decryptedMsgWithPubKey = optionalEncryptionService.get().decryptAndVerify(
                                sealedAndSignedMessage.sealedAndSigned);
                        if (decryptedMsgWithPubKey.message instanceof MailboxMessage) {
                            MailboxMessage mailboxMessage = (MailboxMessage) decryptedMsgWithPubKey.message;
                            Address senderAddress = mailboxMessage.getSenderAddress();
                            checkNotNull(senderAddress, "senderAddress must not be null for mailbox messages");

                            mailboxMap.put(decryptedMsgWithPubKey, mailboxData);
                            log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress="
                                    + senderAddress + " / my address=" + getAddress());
                            decryptedMailboxListeners.stream().forEach(
                                    e -> e.onMailboxMessageAdded(decryptedMsgWithPubKey, senderAddress));
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

    public void sendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing,
                                            MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall("message " + message);
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        checkArgument(!optionalKeyRing.get().getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");
        try {
            checkAuthentication();
            if (authenticatedPeerAddresses.contains(peerAddress)) {
                trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
            } else {
                peerManager.authenticateToDirectMessagePeer(peerAddress,
                        () -> trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener),
                        () -> {
                            log.info("We cannot authenticate to peer. Peer might be offline. We will store message in mailbox.");
                            trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
                        });
            }
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            //TODO check if boolean return type can avoid throwing an exception
            throw new RuntimeException(e);
        }
    }

    // send message and if it fails (peer offline) we store the data to the network
    private void trySendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing,
                                                MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        checkArgument(optionalEncryptionService.isPresent(), "EncryptionService not set. Seems that is called on a seed node which must not happen.");
        try {
            SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(
                    optionalEncryptionService.get().encryptAndSign(peersPubKeyRing, message), peerAddress.getAddressPrefixHash());
            SettableFuture<Connection> future = networkNode.sendMessage(peerAddress, sealedAndSignedMessage);
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
                    log.trace("create MailboxEntry with peerAddress " + peerAddress);
                    PublicKey receiverStoragePublicKey = peersPubKeyRing.getSignaturePubKey();
                    addMailboxData(new ExpirableMailboxPayload(sealedAndSignedMessage,
                                    optionalKeyRing.get().getSignatureKeyPair().getPublic(),
                                    receiverStoragePublicKey),
                            receiverStoragePublicKey);
                    sendMailboxMessageListener.onStoredInMailbox();
                }
            });
        } catch (CryptoException e) {
            log.error("sendEncryptedMessage failed");
            e.printStackTrace();
            sendMailboxMessageListener.onFault();
        }
    }

    private void addMailboxData(ExpirableMailboxPayload expirableMailboxPayload, PublicKey receiversPublicKey) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        try {
            checkAuthentication();
            ProtectedMailboxData protectedMailboxData = dataStorage.getMailboxDataWithSignedSeqNr(
                    expirableMailboxPayload,
                    optionalKeyRing.get().getSignatureKeyPair(),
                    receiversPublicKey);
            dataStorage.add(protectedMailboxData, networkNode.getAddress());
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            //TODO check if boolean return type can avoid throwing an exception
            throw new RuntimeException(e);
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
        }
    }

    public void removeEntryFromMailbox(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        try {
            checkAuthentication();
            if (mailboxMap.containsKey(decryptedMsgWithPubKey)) {
                ProtectedMailboxData mailboxData = mailboxMap.get(decryptedMsgWithPubKey);
                if (mailboxData != null && mailboxData.expirablePayload instanceof ExpirableMailboxPayload) {
                    ExpirableMailboxPayload expirableMailboxPayload = (ExpirableMailboxPayload) mailboxData.expirablePayload;
                    PublicKey receiversPubKey = mailboxData.receiversPubKey;
                    checkArgument(receiversPubKey.equals(optionalKeyRing.get().getSignatureKeyPair().getPublic()),
                            "receiversPubKey is not matching with our key. That must not happen.");
                    try {
                        ProtectedMailboxData protectedMailboxData = dataStorage.getMailboxDataWithSignedSeqNr(
                                expirableMailboxPayload,
                                optionalKeyRing.get().getSignatureKeyPair(),
                                receiversPubKey);
                        dataStorage.removeMailboxData(protectedMailboxData, networkNode.getAddress());
                    } catch (CryptoException e) {
                        log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
                    }

                    mailboxMap.remove(decryptedMsgWithPubKey);
                    log.trace("Removed successfully decryptedMsgWithPubKey.");
                }
            } else {
                log.warn("decryptedMsgWithPubKey not found in mailboxMap. That should never happen." +
                        "\ndecryptedMsgWithPubKey={}\nmailboxMap={}", decryptedMsgWithPubKey, mailboxMap);
            }
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            //TODO check if boolean return type can avoid throwing an exception
            throw new RuntimeException(e);
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
        try {
            checkAuthentication();
            ProtectedData protectedData = dataStorage.getDataWithSignedSeqNr(expirablePayload, optionalKeyRing.get().getSignatureKeyPair());
            if (rePublish)
                return dataStorage.rePublish(protectedData, networkNode.getAddress());
            else
                return dataStorage.add(protectedData, networkNode.getAddress());
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            return false;
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public boolean removeData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        checkArgument(optionalKeyRing.isPresent(), "keyRing not set. Seems that is called on a seed node which must not happen.");
        try {
            checkAuthentication();
            ProtectedData protectedData = dataStorage.getDataWithSignedSeqNr(expirablePayload, optionalKeyRing.get().getSignatureKeyPair());
            return dataStorage.remove(protectedData, networkNode.getAddress());
        } catch (AuthenticationException e) {
            log.error(e.getMessage());
            return false;
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDecryptedMailListener(DecryptedMailListener listener) {
        decryptedMailListeners.add(listener);
    }

    public void removeDecryptedMailListener(DecryptedMailListener listener) {
        decryptedMailListeners.remove(listener);
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        decryptedMailboxListeners.add(listener);
    }

    public void removeDecryptedMailboxListener(DecryptedMailboxListener listener) {
        decryptedMailboxListeners.remove(listener);
    }

    public void addP2PServiceListener(P2PServiceListener listener) {
        p2pServiceListeners.add(listener);
    }

    public void removeP2PServiceListener(P2PServiceListener listener) {
        p2pServiceListeners.remove(listener);
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        dataStorage.addHashMapChangedListener(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isAuthenticated() {
        return !notAuthenticated.get();
    }

    public NetworkNode getNetworkNode() {
        return networkNode;
    }

    public PeerManager getPeerManager() {
        return peerManager;
    }

    public Address getAddress() {
        return networkNode.getAddress();
    }

    public Set<Address> getAuthenticatedPeerAddresses() {
        return authenticatedPeerAddresses;
    }

    @NotNull
    public ReadOnlyIntegerProperty getNumAuthenticatedPeers() {
        return numAuthenticatedPeers;
    }

    public Map<ByteArray, ProtectedData> getDataMap() {
        return dataStorage.getMap();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean verifyAddressPrefixHash(SealedAndSignedMessage sealedAndSignedMessage) {
        if (myOnionAddress != null) {
            byte[] blurredAddressHash = myOnionAddress.getAddressPrefixHash();
            return blurredAddressHash != null &&
                    Arrays.equals(blurredAddressHash, sealedAndSignedMessage.addressPrefixHash);
        } else {
            log.debug("myOnionAddress is null at verifyAddressPrefixHash. That is expected at startup.");
            return false;
        }
    }

    private void checkAuthentication() throws AuthenticationException {
        Log.traceCall();
        if (authenticatedPeerAddresses.isEmpty())
            throw new AuthenticationException("You must be authenticated before adding data to the P2P network.");
    }
}
