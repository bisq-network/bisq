package io.bitsquare.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.Log;
import io.bitsquare.app.ProgramArguments;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.common.crypto.SealedAndSigned;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.crypto.SealedAndSignedMessage;
import io.bitsquare.p2p.messaging.*;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.Peer;
import io.bitsquare.p2p.peers.PeerGroup;
import io.bitsquare.p2p.peers.PeerListener;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.p2p.storage.HashMapChangedListener;
import io.bitsquare.p2p.storage.ProtectedExpirableDataStorage;
import io.bitsquare.p2p.storage.data.ExpirableMailboxPayload;
import io.bitsquare.p2p.storage.data.ExpirablePayload;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;
import io.bitsquare.p2p.storage.messages.GetDataRequest;
import io.bitsquare.p2p.storage.messages.GetDataResponse;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleIntegerProperty;
import org.fxmisc.easybind.EasyBind;
import org.fxmisc.easybind.monadic.MonadicBinding;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents our node in the P2P network
 */
public class P2PService implements SetupListener, MessageListener, ConnectionListener, PeerListener {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);

    private final SeedNodesRepository seedNodesRepository;
    private final int port;
    private final File torDir;
    private final boolean useLocalhost;
    @Nullable
    private final EncryptionService encryptionService;
    private final KeyRing keyRing;
    private final File storageDir;

    private NetworkNode networkNode;
    private PeerGroup peerGroup;
    private ProtectedExpirableDataStorage dataStorage;
    private final CopyOnWriteArraySet<DecryptedMailListener> decryptedMailListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    private final CopyOnWriteArraySet<P2PServiceListener> p2pServiceListeners = new CopyOnWriteArraySet<>();
    private final Map<DecryptedMsgWithPubKey, ProtectedMailboxData> mailboxMap = new HashMap<>();
    private volatile boolean shutDownInProgress;
    private Address connectedSeedNode;
    private final Set<Address> authenticatedPeerAddresses = new HashSet<>();
    private boolean shutDownComplete;
    private final CopyOnWriteArraySet<Runnable> shutDownResultHandlers = new CopyOnWriteArraySet<>();
    private final BooleanProperty hiddenServicePublished = new SimpleBooleanProperty();
    private final BooleanProperty requestingDataCompleted = new SimpleBooleanProperty();
    private final BooleanProperty authenticated = new SimpleBooleanProperty();
    private MonadicBinding<Boolean> readyForAuthentication;
    public final IntegerProperty numAuthenticatedPeers = new SimpleIntegerProperty(0);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public P2PService(SeedNodesRepository seedNodesRepository,
                      @Named(ProgramArguments.PORT_KEY) int port,
                      @Named(ProgramArguments.TOR_DIR) File torDir,
                      @Named(ProgramArguments.USE_LOCALHOST) boolean useLocalhost,
                      @Named(ProgramArguments.NETWORK_ID) int networkId,
                      @Nullable EncryptionService encryptionService,
                      KeyRing keyRing,
                      @Named("storage.dir") File storageDir) {
        Log.traceCall();
        this.seedNodesRepository = seedNodesRepository;
        this.port = port;
        this.torDir = torDir;
        this.useLocalhost = useLocalhost;
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;
        this.storageDir = storageDir;

        init(networkId);
    }

    private void init(int networkId) {
        Log.traceCall();
        // network 
        networkNode = useLocalhost ? new LocalhostNetworkNode(port) : new TorNetworkNode(port, torDir);
        Set<Address> seedNodeAddresses = seedNodesRepository.geSeedNodeAddresses(useLocalhost, networkId);

        // peer group 
        peerGroup = new PeerGroup(networkNode, seedNodeAddresses);
        if (useLocalhost) PeerGroup.setSimulateAuthTorNode(2 * 100);

        // storage 
        dataStorage = new ProtectedExpirableDataStorage(peerGroup, storageDir);


        networkNode.addConnectionListener(this);
        networkNode.addMessageListener(this);
        peerGroup.addPeerListener(this);

        dataStorage.addHashMapChangedListener(new HashMapChangedListener() {
            @Override
            public void onAdded(ProtectedData entry) {
                Log.traceCall();
                if (entry instanceof ProtectedMailboxData)
                    tryDecryptMailboxData((ProtectedMailboxData) entry);
            }

            @Override
            public void onRemoved(ProtectedData entry) {
                Log.traceCall();
            }
        });

        readyForAuthentication = EasyBind.combine(hiddenServicePublished, requestingDataCompleted, authenticated,
                (a, b, c) -> a && b && !c);
        readyForAuthentication.subscribe((observable, oldValue, newValue) -> {
            // we need to have both the initial data delivered and the hidden service published before we 
            // bootstrap and authenticate to other nodes. 
            if (newValue)
                tryAuthenticateSeedNode();
        });

        requestingDataCompleted.addListener((observable, oldValue, newValue) -> {
            if (newValue)
                p2pServiceListeners.stream().forEach(e -> e.onRequestingDataCompleted());
        });
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof GetDataRequest) {
            Log.traceCall(message.toString());
            log.info("Received GetDataSetMessage: " + message);
            networkNode.sendMessage(connection, new GetDataResponse(getDataSet()));
        } else if (message instanceof GetDataResponse) {
            Log.traceCall(message.toString());
            GetDataResponse getDataResponse = (GetDataResponse) message;
            log.info("Received GetDataResponse: " + message);
            HashSet<ProtectedData> set = getDataResponse.set;
            if (!set.isEmpty()) {
                // we keep that connection open as the bootstrapping peer will use that for the authentication
                // as we are not authenticated yet the data adding will not be broadcasted 
                set.stream().forEach(e -> dataStorage.add(e, connection.getPeerAddress()));
            } else {
                log.trace("Received DataSetMessage: Empty data set");
            }
            setRequestingDataCompleted();
        } else if (message instanceof SealedAndSignedMessage) {
            Log.traceCall(message.toString());
            if (encryptionService != null) {
                try {
                    SealedAndSignedMessage sealedAndSignedMessage = (SealedAndSignedMessage) message;
                    DecryptedMsgWithPubKey decryptedMsgWithPubKey = encryptionService.decryptAndVerify(
                            sealedAndSignedMessage.sealedAndSigned);
                    log.info("Received SealedAndSignedMessage and decrypted it: " + decryptedMsgWithPubKey);
                    decryptedMailListeners.stream().forEach(
                            e -> e.onMailMessage(decryptedMsgWithPubKey, connection.getPeerAddress()));
                } catch (CryptoException e) {
                    log.info("Decryption of SealedAndSignedMessage failed. " +
                            "That is expected if the message is not intended for us.");
                }
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
        Log.traceCall();
        checkArgument(peerAddress.equals(connection.getPeerAddress()),
                "peerAddress must match connection.getPeerAddress()");
        authenticatedPeerAddresses.add(peerAddress);
        authenticated.set(true);

        p2pServiceListeners.stream().forEach(e -> e.onFirstPeerAuthenticated());

        numAuthenticatedPeers.set(authenticatedPeerAddresses.size());
    }

    @Override
    public void onDisconnect(Reason reason, Connection connection) {
        Log.traceCall();
        if (connection.isAuthenticated())
            authenticatedPeerAddresses.remove(connection.getPeerAddress());

        numAuthenticatedPeers.set(authenticatedPeerAddresses.size());
    }

    @Override
    public void onError(Throwable throwable) {
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PeerListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onFirstAuthenticatePeer(Peer peer) {
        Log.traceCall();
        log.trace("onFirstAuthenticatePeer " + peer);
        sendGetAllDataMessageAfterAuthentication(peer);

    }

    @Override
    public void onPeerAdded(Peer peer) {
        Log.traceCall();
    }

    @Override
    public void onPeerRemoved(Address address) {
        Log.traceCall();
    }

    @Override
    public void onConnectionAuthenticated(Connection connection) {
        Log.traceCall();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // SetupListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onTorNodeReady() {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onTorNodeReady());

        // 1. Step: As soon we have the tor node ready (hidden service still not available) we request the
        //          data set from a random seed node. 
        sendGetDataRequest(peerGroup.getSeedNodeAddresses());
    }

    @Override
    public void onHiddenServicePublished() {
        Log.traceCall();
        checkArgument(networkNode.getAddress() != null, "Address must be set when we have the hidden service ready");

        p2pServiceListeners.stream().forEach(e -> e.onHiddenServicePublished());

        // 3. (or 2.). Step: Hidden service is published
        hiddenServicePublished.set(true);
    }

    @Override
    public void onSetupFailed(Throwable throwable) {
        Log.traceCall();
        p2pServiceListeners.stream().forEach(e -> e.onSetupFailed(throwable));
    }

    private void sendGetDataRequest(Collection<Address> seedNodeAddresses) {
        Log.traceCall();
        if (!seedNodeAddresses.isEmpty()) {
            List<Address> remainingSeedNodeAddresses = new ArrayList<>(seedNodeAddresses);
            Collections.shuffle(remainingSeedNodeAddresses);
            Address candidate = remainingSeedNodeAddresses.remove(0);
            log.info("We try to send a GetAllDataMessage request to a random seed node. " + candidate);

            SettableFuture<Connection> future = networkNode.sendMessage(candidate, new GetDataRequest());
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    log.info("Send GetAllDataMessage to " + candidate + " succeeded.");
                    connectedSeedNode = candidate;
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    log.info("Send GetAllDataMessage to " + candidate + " failed. " +
                            "That is expected if other seed nodes are offline." +
                            "\nException:" + throwable.getMessage());
                    if (!remainingSeedNodeAddresses.isEmpty())
                        log.trace("We try to connect another random seed node. " + remainingSeedNodeAddresses);

                    sendGetDataRequest(remainingSeedNodeAddresses);
                }
            });
        } else {
            log.info("There is no seed node available for requesting data. That is expected for the first seed node.");
            setRequestingDataCompleted();
        }
    }

    private void setRequestingDataCompleted() {
        Log.traceCall();
        // 2. (or 3.) Step: We got all data loaded
        if (!requestingDataCompleted.get())
            requestingDataCompleted.set(true);
    }

    // 4. Step: hiddenServicePublished and allDataLoaded. We start authenticate to the connected seed node.
    private void tryAuthenticateSeedNode() {
        Log.traceCall();
        if (connectedSeedNode != null) {
            log.trace("authenticateSeedNode");
            peerGroup.authenticateSeedNode(connectedSeedNode);
        } else {
            log.debug("No connected seedNode available.");
        }
    }

    // 5. Step: 
    private void sendGetAllDataMessageAfterAuthentication(final Peer peer) {
        Log.traceCall();
        log.trace("sendGetDataSetMessageAfterAuthentication");
        // After authentication we request again data as we might have missed pushed data in the meantime
        SettableFuture<Connection> future = networkNode.sendMessage(peer.connection, new GetDataRequest());
        Futures.addCallback(future, new FutureCallback<Connection>() {
            @Override
            public void onSuccess(@Nullable Connection connection) {
                log.info("onPeerAddressAuthenticated Send GetAllDataMessage to " + peer.address + " succeeded.");
            }

            @Override
            public void onFailure(@NotNull Throwable throwable) {
                log.warn("onPeerAddressAuthenticated Send GetAllDataMessage to " + peer.address + " failed. " +
                        "Exception:" + throwable.getMessage());
            }
        });
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // used by seed nodes to exclude themselves form list
    public void removeMySeedNodeAddressFromList(Address mySeedNodeAddress) {
        Log.traceCall();
        peerGroup.removeMySeedNodeAddressFromList(mySeedNodeAddress);
    }

    public void start() {
        Log.traceCall();
        start(null);
    }

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

            if (peerGroup != null)
                peerGroup.shutDown();

            if (networkNode != null)
                networkNode.shutDown(() -> {
                    shutDownResultHandlers.stream().forEach(e -> e.run());
                    shutDownComplete = true;
                });
        } else {
            if (shutDownComplete)
                shutDownCompleteHandler.run();
            else
                shutDownResultHandlers.add(shutDownCompleteHandler);
            log.warn("shutDown already in progress");
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Messaging
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message,
                                         SendMailMessageListener sendMailMessageListener) {
        Log.traceCall();
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailMessage)");
        checkAuthentication();

        if (!authenticatedPeerAddresses.contains(peerAddress))
            peerGroup.authenticateToPeer(peerAddress,
                    () -> doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener),
                    () -> sendMailMessageListener.onFault());
        else
            doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener);
    }

    private void doSendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message,
                                            SendMailMessageListener sendMailMessageListener) {
        Log.traceCall();
        if (encryptionService != null) {
            try {
                SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(
                        encryptionService.encryptAndSign(pubKeyRing, message));
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
    }

    public void sendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing,
                                            MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall();
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");
        checkAuthentication();

        if (authenticatedPeerAddresses.contains(peerAddress)) {
            trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
        } else {
            peerGroup.authenticateToPeer(peerAddress,
                    () -> trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener),
                    () -> {
                        log.info("We cannot authenticate to peer. Peer might be offline. We will store message in mailbox.");
                        trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
                    });
        }
    }

    private void trySendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing,
                                                MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        Log.traceCall();
        if (encryptionService != null) {
            try {
                SealedAndSignedMessage sealedAndSignedMessage = new SealedAndSignedMessage(
                        encryptionService.encryptAndSign(peersPubKeyRing, message));
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
                                        keyRing.getSignatureKeyPair().getPublic(),
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
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Data storage
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        checkAuthentication();

        try {
            return dataStorage.add(dataStorage.getDataWithSignedSeqNr(expirablePayload,
                    keyRing.getSignatureKeyPair()), networkNode.getAddress());
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    private void addMailboxData(ExpirableMailboxPayload expirableMailboxPayload, PublicKey receiversPublicKey) {
        Log.traceCall();
        checkAuthentication();

        try {
            dataStorage.add(dataStorage.getMailboxDataWithSignedSeqNr(expirableMailboxPayload,
                    keyRing.getSignatureKeyPair(), receiversPublicKey), networkNode.getAddress());
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
        }
    }

    public boolean removeData(ExpirablePayload expirablePayload) {
        Log.traceCall();
        checkAuthentication();

        try {
            return dataStorage.remove(dataStorage.getDataWithSignedSeqNr(expirablePayload,
                    keyRing.getSignatureKeyPair()), networkNode.getAddress());
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public void removeEntryFromMailbox(DecryptedMsgWithPubKey decryptedMsgWithPubKey) {
        Log.traceCall();
        checkAuthentication();

        if (mailboxMap.containsKey(decryptedMsgWithPubKey)) {
            ProtectedMailboxData mailboxData = mailboxMap.get(decryptedMsgWithPubKey);
            if (mailboxData != null && mailboxData.expirablePayload instanceof ExpirableMailboxPayload) {
                checkArgument(mailboxData.receiversPubKey.equals(keyRing.getSignatureKeyPair().getPublic()),
                        "mailboxData.receiversPubKey is not matching with our key. That must not happen.");
                removeMailboxData((ExpirableMailboxPayload) mailboxData.expirablePayload, mailboxData.receiversPubKey);
                mailboxMap.remove(decryptedMsgWithPubKey);
                log.trace("Removed successfully protectedExpirableData.");

            }
        } else {
            log.warn("decryptedMsgWithPubKey not found in mailboxMap. That should never happen." +
                    "\ndecryptedMsgWithPubKey={}\nmailboxMap={}", decryptedMsgWithPubKey, mailboxMap);
        }
    }

    private void removeMailboxData(ExpirableMailboxPayload expirableMailboxPayload, PublicKey receiversPublicKey) {
        Log.traceCall();
        checkAuthentication();

        try {
            dataStorage.removeMailboxData(dataStorage.getMailboxDataWithSignedSeqNr(expirableMailboxPayload,
                    keyRing.getSignatureKeyPair(), receiversPublicKey), networkNode.getAddress());
        } catch (CryptoException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
        }
    }

    public Map<BigInteger, ProtectedData> getDataMap() {
        Log.traceCall();
        return dataStorage.getMap();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDecryptedMailListener(DecryptedMailListener listener) {
        Log.traceCall();
        decryptedMailListeners.add(listener);
    }

    public void removeDecryptedMailListener(DecryptedMailListener listener) {
        Log.traceCall();
        decryptedMailListeners.remove(listener);
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        Log.traceCall();
        decryptedMailboxListeners.add(listener);
    }

    public void removeDecryptedMailboxListener(DecryptedMailboxListener listener) {
        Log.traceCall();
        decryptedMailboxListeners.remove(listener);
    }

    public void addP2PServiceListener(P2PServiceListener listener) {
        Log.traceCall();
        p2pServiceListeners.add(listener);
    }

    public void removeP2PServiceListener(P2PServiceListener listener) {
        Log.traceCall();
        p2pServiceListeners.remove(listener);
    }

    public void addHashSetChangedListener(HashMapChangedListener hashMapChangedListener) {
        Log.traceCall();
        dataStorage.addHashMapChangedListener(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean isAuthenticated() {
        Log.traceCall();
        return authenticated.get();
    }

    public NetworkNode getNetworkNode() {
        Log.traceCall();
        return networkNode;
    }

    public PeerGroup getPeerGroup() {
        Log.traceCall();
        return peerGroup;
    }

    public Address getAddress() {
        Log.traceCall();
        return networkNode.getAddress();
    }

    public Set<Address> getAuthenticatedPeerAddresses() {
        return authenticatedPeerAddresses;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private HashSet<ProtectedData> getDataSet() {
        // Log.traceCall();
        return new HashSet<>(dataStorage.getMap().values());
    }

    private void tryDecryptMailboxData(ProtectedMailboxData mailboxData) {
        Log.traceCall();
        if (encryptionService != null) {
            ExpirablePayload data = mailboxData.expirablePayload;
            if (data instanceof ExpirableMailboxPayload) {
                ExpirableMailboxPayload mailboxEntry = (ExpirableMailboxPayload) data;
                SealedAndSigned sealedAndSigned = mailboxEntry.sealedAndSignedMessage.sealedAndSigned;
                try {
                    DecryptedMsgWithPubKey decryptedMsgWithPubKey = encryptionService.decryptAndVerify(sealedAndSigned);
                    if (decryptedMsgWithPubKey.message instanceof MailboxMessage) {
                        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMsgWithPubKey.message;
                        Address senderAddress = mailboxMessage.getSenderAddress();
                        checkNotNull(senderAddress, "senderAddress must not be null for mailbox messages");

                        mailboxMap.put(decryptedMsgWithPubKey, mailboxData);
                        log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress="
                                + senderAddress + " / my address=" + getAddress());
                        decryptedMailboxListeners.stream().forEach(
                                e -> e.onMailboxMessageAdded(decryptedMsgWithPubKey, senderAddress));
                    }
                } catch (CryptoException e) {
                    log.trace("Decryption of SealedAndSignedMessage failed. That is expected if the message is not intended for us. " + e.getMessage());
                }
            }
        }
    }

    private void checkAuthentication() {
        Log.traceCall();
        if (authenticatedPeerAddresses.isEmpty())
            throw new AuthenticationException("You must be authenticated before adding data to the P2P network.");
    }
}
