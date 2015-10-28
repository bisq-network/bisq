package io.bitsquare.p2p;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.google.inject.name.Named;
import io.bitsquare.app.ProgramArguments;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.KeyRing;
import io.bitsquare.common.crypto.PubKeyRing;
import io.bitsquare.crypto.EncryptionService;
import io.bitsquare.p2p.messaging.*;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.routing.Neighbor;
import io.bitsquare.p2p.routing.Routing;
import io.bitsquare.p2p.routing.RoutingListener;
import io.bitsquare.p2p.seed.SeedNodesRepository;
import io.bitsquare.p2p.storage.HashSetChangedListener;
import io.bitsquare.p2p.storage.ProtectedExpirableDataStorage;
import io.bitsquare.p2p.storage.data.ExpirableMailboxPayload;
import io.bitsquare.p2p.storage.data.ExpirablePayload;
import io.bitsquare.p2p.storage.data.ProtectedData;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;
import io.bitsquare.p2p.storage.messages.DataSetMessage;
import io.bitsquare.p2p.storage.messages.GetDataSetMessage;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Represents our node in the P2P network
 */
public class P2PService {
    private static final Logger log = LoggerFactory.getLogger(P2PService.class);

    private final EncryptionService encryptionService;
    private final SetupListener setupListener;
    private KeyRing keyRing;
    private final NetworkStatistics networkStatistics;

    private final NetworkNode networkNode;
    private final Routing routing;
    private final ProtectedExpirableDataStorage dataStorage;
    private final List<DecryptedMailListener> decryptedMailListeners = new CopyOnWriteArrayList<>();
    private final List<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArrayList<>();
    private final List<P2PServiceListener> p2pServiceListeners = new CopyOnWriteArrayList<>();
    private final Map<DecryptedMessageWithPubKey, ProtectedMailboxData> mailboxMap = new ConcurrentHashMap<>();
    private volatile boolean shutDownInProgress;
    private List<Address> seedNodeAddresses;
    private List<Address> connectedSeedNodes = new CopyOnWriteArrayList<>();
    private Set<Address> authenticatedPeerAddresses = new HashSet<>();
    private boolean authenticatedToFirstPeer;
    private boolean allDataReceived;
    public boolean authenticated;
    private boolean shutDownComplete;
    private List<Runnable> shutDownResultHandlers = new CopyOnWriteArrayList<>();
    private final List<Long> getDataSetMessageNonceList = new ArrayList<>();
    private boolean allSeedNodesRequested;
    private Timer sendGetAllDataMessageTimer;
    private volatile boolean hiddenServiceReady;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public P2PService(SeedNodesRepository seedNodesRepository,
                      @Named(ProgramArguments.PORT_KEY) int port,
                      @Named(ProgramArguments.TOR_DIR) File torDir,
                      @Named(ProgramArguments.USE_LOCALHOST) boolean useLocalhost,
                      EncryptionService encryptionService,
                      KeyRing keyRing) {
        this.encryptionService = encryptionService;
        this.keyRing = keyRing;

        networkStatistics = new NetworkStatistics();

        // network layer
        if (useLocalhost) {
            networkNode = new LocalhostNetworkNode(port);
            seedNodeAddresses = seedNodesRepository.getLocalhostSeedNodeAddresses();
        } else {
            networkNode = new TorNetworkNode(port, torDir);
            seedNodeAddresses = seedNodesRepository.getTorSeedNodeAddresses();
        }

        // routing layer
        routing = new Routing(networkNode, seedNodeAddresses);


        // storage layer
        dataStorage = new ProtectedExpirableDataStorage(routing, encryptionService);


        // Listeners
        setupListener = new SetupListener() {
            @Override
            public void onTorNodeReady() {
                UserThread.execute(() -> p2pServiceListeners.stream().forEach(e -> e.onTorNodeReady()));

                // we don't know yet our own address so we can not filter that from the 
                // seedNodeAddresses in case we are a seed node
                sendGetAllDataMessage(seedNodeAddresses);
            }

            @Override
            public void onHiddenServiceReady() {
                hiddenServiceReady = true;
                tryStartAuthentication();

                UserThread.execute(() -> p2pServiceListeners.stream().forEach(e -> e.onHiddenServiceReady()));
            }

            @Override
            public void onSetupFailed(Throwable throwable) {
                UserThread.execute(() -> p2pServiceListeners.stream().forEach(e -> e.onSetupFailed(throwable)));
            }
        };
        
        networkNode.addConnectionListener(new ConnectionListener() {
            @Override
            public void onConnection(Connection connection) {
            }

            @Override
            public void onPeerAddressAuthenticated(Address peerAddress, Connection connection) {
                authenticatedPeerAddresses.add(peerAddress);
                authenticatedToFirstPeer = true;

                P2PService.this.authenticated = true;
                dataStorage.setAuthenticated(true);
                UserThread.execute(() -> p2pServiceListeners.stream().forEach(e -> e.onAuthenticated()));
            }

            @Override
            public void onDisconnect(Reason reason, Connection connection) {
                Address peerAddress = connection.getPeerAddress();
                if (peerAddress != null)
                    authenticatedPeerAddresses.remove(peerAddress);
            }

            @Override
            public void onError(Throwable throwable) {
                log.error("onError self/ConnectionException " + networkNode.getAddress() + "/" + throwable);
            }
        });

        networkNode.addMessageListener((message, connection) -> {
            if (message instanceof GetDataSetMessage) {
                log.trace("Received GetAllDataMessage: " + message);

                // we only reply if we did not get the message form ourselves (in case we are a seed node)
                if (!getDataSetMessageNonceList.contains(((GetDataSetMessage) message).nonce)) {
                    networkNode.sendMessage(connection, new DataSetMessage(getHashSet()));
                } else {
                    connection.shutDown(() -> {
                        if (allSeedNodesRequested) dataReceived();
                    });
                }
            } else if (message instanceof DataSetMessage) {
                log.trace("Received AllDataMessage: " + message);
                // we keep that connection open as the bootstrapping peer will use that for the authentication

                // as we are not authenticated yet the data adding will not be broadcasted 
                HashSet<ProtectedData> set = ((DataSetMessage) message).set;
                set.stream().forEach(e -> dataStorage.add(e, connection.getPeerAddress()));

                set.stream().filter(e -> e instanceof ProtectedMailboxData).forEach(e -> tryDecryptMailboxData((ProtectedMailboxData) e));

                dataReceived();
            } else if (message instanceof SealedAndSignedMessage) {
                try {
                    DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerifyMessage((SealedAndSignedMessage) message);
                    UserThread.execute(() -> decryptedMailListeners.stream().forEach(e -> e.onMailMessage(decryptedMessageWithPubKey, connection.getPeerAddress())));
                } catch (CryptoException e) {
                    log.info("Decryption of SealedAndSignedMessage failed. That is expected if the message is not intended for us.");
                }
            }
        });

        routing.addRoutingListener(new RoutingListener() {
            @Override
            public void onFirstNeighborAdded(Neighbor neighbor) {
                log.trace("onFirstNeighbor " + neighbor.toString());
            }

            @Override
            public void onNeighborAdded(Neighbor neighbor) {

            }

            @Override
            public void onNeighborRemoved(Address address) {

            }

            @Override
            public void onConnectionAuthenticated(Connection connection) {

            }
        });

        dataStorage.addHashSetChangedListener(new HashSetChangedListener() {
            @Override
            public void onAdded(ProtectedData entry) {
                if (entry instanceof ProtectedMailboxData)
                    tryDecryptMailboxData((ProtectedMailboxData) entry);
            }

            @Override
            public void onRemoved(ProtectedData entry) {
            }
        });
    }

    public void protocol() {
        // networkNode.start
        //      onTorNodeReady: sendGetAllDataMessage
        //      onHiddenServiceReady: tryStartAuthentication
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // startup sequence
    //      networkNode.start
    //          SetupListener.onTorNodeReady: sendGetAllDataMessage
    //          SetupListener.onHiddenServiceReady: tryStartAuthentication
    //              if hiddenServiceReady && allDataReceived) routing.startAuthentication
    //              ConnectionListener.onPeerAddressAuthenticated

    public void start() {
        start(null);
    }

    public void start(@Nullable P2PServiceListener listener) {
        if (listener != null)
            addP2PServiceListener(listener);

        networkNode.start(setupListener);
    }

    public void shutDown(Runnable shutDownCompleteHandler) {
        if (!shutDownInProgress) {
            shutDownInProgress = true;

            shutDownResultHandlers.add(shutDownCompleteHandler);

            if (sendGetAllDataMessageTimer != null)
                sendGetAllDataMessageTimer.cancel();

            if (dataStorage != null)
                dataStorage.shutDown();

            if (routing != null)
                routing.shutDown();

            if (networkNode != null)
                networkNode.shutDown(() -> {
                    UserThread.execute(() -> shutDownResultHandlers.stream().forEach(e -> new Thread(e).start()));
                    shutDownComplete = true;
                });
        } else {
            if (shutDownComplete)
                new Thread(shutDownCompleteHandler).start();
            else
                shutDownResultHandlers.add(shutDownCompleteHandler);
            log.warn("shutDown already in progress");
        }
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public void removeEntryFromMailbox(DecryptedMessageWithPubKey decryptedMessageWithPubKey) {
        log.trace("removeEntryFromMailbox");
        ProtectedMailboxData mailboxData = mailboxMap.get(decryptedMessageWithPubKey);
        if (mailboxData != null && mailboxData.expirablePayload instanceof ExpirableMailboxPayload) {
            checkArgument(mailboxData.receiversPubKey.equals(keyRing.getStorageSignatureKeyPair().getPublic()),
                    "mailboxData.receiversPubKey is not matching with our key. That must not happen.");
            removeMailboxData((ExpirableMailboxPayload) mailboxData.expirablePayload, mailboxData.receiversPubKey);
            mailboxMap.remove(decryptedMessageWithPubKey);
            log.trace("Removed successfully protectedExpirableData.");
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Messaging
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void sendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message, SendMailMessageListener sendMailMessageListener) {
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailMessage)");

        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before sending direct messages.");

        if (!authenticatedPeerAddresses.contains(peerAddress))
            routing.authenticateToPeer(peerAddress,
                    () -> doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener),
                    () -> UserThread.execute(() -> sendMailMessageListener.onFault()));
        else
            doSendEncryptedMailMessage(peerAddress, pubKeyRing, message, sendMailMessageListener);
    }

    private void doSendEncryptedMailMessage(Address peerAddress, PubKeyRing pubKeyRing, MailMessage message, SendMailMessageListener sendMailMessageListener) {
        try {
            SealedAndSignedMessage sealedAndSignedMessage = encryptionService.encryptAndSignMessage(pubKeyRing, message);
            SettableFuture<Connection> future = sendMessage(peerAddress, sealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    UserThread.execute(() -> sendMailMessageListener.onArrived());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    throwable.printStackTrace();
                    UserThread.execute(() -> sendMailMessageListener.onFault());
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            UserThread.execute(() -> sendMailMessageListener.onFault());
        }
    }

    public void sendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing, MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        checkNotNull(peerAddress, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");

        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before sending direct messages.");

        if (authenticatedPeerAddresses.contains(peerAddress)) {
            trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
        } else {
            routing.authenticateToPeer(peerAddress,
                    () -> trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener),
                    () -> {
                        log.info("We cannot authenticate to peer. Peer might be offline. We will store message in mailbox.");
                        trySendEncryptedMailboxMessage(peerAddress, peersPubKeyRing, message, sendMailboxMessageListener);
                    });
        }
    }

    private void trySendEncryptedMailboxMessage(Address peerAddress, PubKeyRing peersPubKeyRing, MailboxMessage message, SendMailboxMessageListener sendMailboxMessageListener) {
        try {
            SealedAndSignedMessage sealedAndSignedMessage = encryptionService.encryptAndSignMessage(peersPubKeyRing, message);
            SettableFuture<Connection> future = sendMessage(peerAddress, sealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    log.trace("SendEncryptedMailboxMessage onSuccess");
                    UserThread.execute(() -> sendMailboxMessageListener.onArrived());
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.trace("SendEncryptedMailboxMessage onFailure");
                    log.debug(throwable.toString());
                    log.info("We cannot send message to peer. Peer might be offline. We will store message in mailbox.");
                    log.trace("create MailboxEntry with peerAddress " + peerAddress);
                    PublicKey receiverStoragePublicKey = peersPubKeyRing.getStorageSignaturePubKey();
                    addMailboxData(new ExpirableMailboxPayload(sealedAndSignedMessage,
                                    keyRing.getStorageSignatureKeyPair().getPublic(),
                                    receiverStoragePublicKey),
                            receiverStoragePublicKey);
                    UserThread.execute(() -> sendMailboxMessageListener.onStoredInMailbox());
                }
            });
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error("sendEncryptedMessage failed");
            UserThread.execute(() -> sendMailboxMessageListener.onFault());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ProtectedData 
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean addData(ExpirablePayload expirablePayload) {
        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before adding data to the P2P network.");

        try {
            return dataStorage.add(dataStorage.getDataWithSignedSeqNr(expirablePayload, keyRing.getStorageSignatureKeyPair()), networkNode.getAddress());
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public boolean addMailboxData(ExpirableMailboxPayload expirableMailboxPayload, PublicKey receiversPublicKey) {
        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before adding data to the P2P network.");

        try {
            return dataStorage.add(dataStorage.getMailboxDataWithSignedSeqNr(expirableMailboxPayload, keyRing.getStorageSignatureKeyPair(), receiversPublicKey), networkNode.getAddress());
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public boolean removeData(ExpirablePayload expirablePayload) {
        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before removing data from the P2P network.");
        try {
            return dataStorage.remove(dataStorage.getDataWithSignedSeqNr(expirablePayload, keyRing.getStorageSignatureKeyPair()), networkNode.getAddress());
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public boolean removeMailboxData(ExpirableMailboxPayload expirableMailboxPayload, PublicKey receiversPublicKey) {
        if (!authenticatedToFirstPeer)
            throw new AuthenticationException("You must be authenticated before removing data from the P2P network.");
        try {
            return dataStorage.removeMailboxData(dataStorage.getMailboxDataWithSignedSeqNr(expirableMailboxPayload, keyRing.getStorageSignatureKeyPair(), receiversPublicKey), networkNode.getAddress());
        } catch (NoSuchAlgorithmException | SignatureException | InvalidKeyException e) {
            log.error("Signing at getDataWithSignedSeqNr failed. That should never happen.");
            return false;
        }
    }

    public Map<BigInteger, ProtectedData> getDataMap() {
        return dataStorage.getMap();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Listeners
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addMessageListener(MessageListener messageListener) {
        networkNode.addMessageListener(messageListener);
    }

    public void removeMessageListener(MessageListener messageListener) {
        networkNode.removeMessageListener(messageListener);
    }

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

    public void addHashSetChangedListener(HashSetChangedListener hashSetChangedListener) {
        dataStorage.addHashSetChangedListener(hashSetChangedListener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Getters
    ///////////////////////////////////////////////////////////////////////////////////////////

    public NetworkNode getNetworkNode() {
        return networkNode;
    }

    public Routing getRouting() {
        return routing;
    }

    public Address getAddress() {
        return networkNode.getAddress();
    }

    public NetworkStatistics getNetworkStatistics() {
        return networkStatistics;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void sendGetAllDataMessage(List<Address> seedNodeAddresses) {
        Address networkNodeAddress = networkNode.getAddress();
        if (networkNodeAddress != null)
            seedNodeAddresses.remove(networkNodeAddress);
        List<Address> remainingSeedNodeAddresses = new CopyOnWriteArrayList<>(seedNodeAddresses);

        if (!seedNodeAddresses.isEmpty()) {
            Collections.shuffle(remainingSeedNodeAddresses);
            Address candidate = remainingSeedNodeAddresses.remove(0);
            log.info("We try to send a GetAllDataMessage request to a random seed node. " + candidate);

            // we use a nonce to see if we are sending to ourselves in case we are a seed node
            // we don't know our own onion address at that moment so we cannot filter seed nodes
            SettableFuture<Connection> future = sendMessage(candidate, new GetDataSetMessage(addToListAndGetNonce()));
            Futures.addCallback(future, new FutureCallback<Connection>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    log.info("Send GetAllDataMessage to " + candidate + " succeeded.");
                    connectedSeedNodes.add(candidate);

                    // we try to connect to 2 seed nodes
                    if (connectedSeedNodes.size() < 2 && !remainingSeedNodeAddresses.isEmpty()) {
                        // give a random pause of 1-3 sec. before using the next
                        sendGetAllDataMessageTimer = new Timer();
                        sendGetAllDataMessageTimer.schedule(new TimerTask() {
                            @Override
                            public void run() {
                                sendGetAllDataMessage(remainingSeedNodeAddresses);
                            }
                        }, new Random().nextInt(2000) + 1000);
                    } else {
                        allSeedNodesRequested = true;
                    }
                }

                @Override
                public void onFailure(Throwable throwable) {
                    log.info("Send GetAllDataMessage to " + candidate + " failed. Exception:" + throwable.getMessage());
                    log.trace("We try to connect another random seed node. " + remainingSeedNodeAddresses);
                    sendGetAllDataMessage(remainingSeedNodeAddresses);
                }
            });
        } else {
            log.info("There is no seed node available for requesting data. That is expected for the first seed node.");
            dataReceived();
            allSeedNodesRequested = true;
        }
    }

    private long addToListAndGetNonce() {
        long nonce = new Random().nextLong();
        while (nonce == 0) {
            nonce = new Random().nextLong();
        }
        getDataSetMessageNonceList.add(nonce);
        return nonce;
    }

    private void dataReceived() {
        if (!allDataReceived) {
            allDataReceived = true;
            UserThread.execute(() -> p2pServiceListeners.stream().forEach(e -> e.onAllDataReceived()));

            tryStartAuthentication();
        }
    }

    private void tryStartAuthentication() {
        // we need to have both the initial data delivered and the hidden service published before we 
        // bootstrap and authenticate to other nodes
        if (allDataReceived && hiddenServiceReady) {
            // we remove ourselves in case we are a seed node
            checkArgument(networkNode.getAddress() != null, "Address must be set when we are authenticated");
            connectedSeedNodes.remove(networkNode.getAddress());

            routing.startAuthentication(connectedSeedNodes);
        }
    }

    private SettableFuture<Connection> sendMessage(Address peerAddress, Message message) {
        return networkNode.sendMessage(peerAddress, message);
    }

    private HashSet<ProtectedData> getHashSet() {
        return new HashSet<>(dataStorage.getMap().values());
    }

    private void tryDecryptMailboxData(ProtectedMailboxData mailboxData) {
        ExpirablePayload data = mailboxData.expirablePayload;
        if (data instanceof ExpirableMailboxPayload) {
            ExpirableMailboxPayload mailboxEntry = (ExpirableMailboxPayload) data;
            SealedAndSignedMessage sealedAndSignedMessage = mailboxEntry.sealedAndSignedMessage;
            try {
                DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerifyMessage(sealedAndSignedMessage);
                if (decryptedMessageWithPubKey.message instanceof MailboxMessage) {
                    MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.message;
                    Address senderAddress = mailboxMessage.getSenderAddress();
                    checkNotNull(senderAddress, "senderAddress must not be null for mailbox messages");

                    log.trace("mailboxData.publicKey " + mailboxData.ownerStoragePubKey.hashCode());
                    log.trace("keyRing.getStorageSignatureKeyPair().getPublic() " + keyRing.getStorageSignatureKeyPair().getPublic().hashCode());
                    log.trace("keyRing.getMsgSignatureKeyPair().getPublic() " + keyRing.getMsgSignatureKeyPair().getPublic().hashCode());
                    log.trace("keyRing.getMsgEncryptionKeyPair().getPublic() " + keyRing.getMsgEncryptionKeyPair().getPublic().hashCode());


                    mailboxMap.put(decryptedMessageWithPubKey, mailboxData);
                    log.trace("Decryption of SealedAndSignedMessage succeeded. senderAddress=" + senderAddress + " / my address=" + getAddress());
                    UserThread.execute(() -> decryptedMailboxListeners.stream().forEach(e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, senderAddress)));
                }
            } catch (CryptoException e) {
                log.trace("Decryption of SealedAndSignedMessage failed. That is expected if the message is not intended for us.");
            }
        }
    }
}
