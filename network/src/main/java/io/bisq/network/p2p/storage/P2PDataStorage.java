package io.bisq.network.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.Timer;
import io.bisq.common.UserThread;
import io.bisq.common.app.Log;
import io.bisq.common.crypto.CryptoException;
import io.bisq.common.crypto.Sig;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkPayload;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.common.proto.persistable.PersistableEnvelope;
import io.bisq.common.proto.persistable.PersistableHashMap;
import io.bisq.common.proto.persistable.PersistablePayload;
import io.bisq.common.proto.persistable.PersistenceProtoResolver;
import io.bisq.common.storage.FileUtil;
import io.bisq.common.storage.ResourceNotFoundException;
import io.bisq.common.storage.Storage;
import io.bisq.common.util.Tuple2;
import io.bisq.common.util.Utilities;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.crypto.EncryptionService;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.network.*;
import io.bisq.network.p2p.peers.BroadcastHandler;
import io.bisq.network.p2p.peers.Broadcaster;
import io.bisq.network.p2p.storage.messages.*;
import io.bisq.network.p2p.storage.payload.*;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.bouncycastle.util.encoders.Hex;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

// Run in UserThread
public class P2PDataStorage implements MessageListener, ConnectionListener, PersistableEnvelope {
    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    /**
     * How many days to keep an entry before it is purged.
     */
    public static final int PURGE_AGE_DAYS = 10;

    @VisibleForTesting
    public static int CHECK_TTL_INTERVAL_SEC = 60;

    private final Broadcaster broadcaster;
    private final Map<ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private Timer removeExpiredEntriesTimer;
    private SequenceNumberMap sequenceNumberMap = new SequenceNumberMap();
    private final Storage<SequenceNumberMap> sequenceNumberMapStorage;
    private HashMap<ByteArray, ProtectedStorageEntry> persistedMap = new HashMap<>();
    private final Storage<PersistableHashMap<ByteArray, ProtectedStorageEntry>> persistedEntryMapStorage;
    private final NetworkProtoResolver networkProtoResolver;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PDataStorage(Broadcaster broadcaster, NetworkNode networkNode, File storageDir,
                          PersistenceProtoResolver persistenceProtoResolver, NetworkProtoResolver networkProtoResolver) {
        this.broadcaster = broadcaster;
        this.networkProtoResolver = networkProtoResolver;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        sequenceNumberMapStorage = new Storage<>(storageDir, persistenceProtoResolver);
        persistedEntryMapStorage = new Storage<>(storageDir, persistenceProtoResolver);

        init(storageDir);
    }

    private void init(File storageDir) {
        sequenceNumberMapStorage.setNumMaxBackupFiles(5);
        persistedEntryMapStorage.setNumMaxBackupFiles(1);
        SequenceNumberMap persistedSequenceNumberMap = sequenceNumberMapStorage.initAndGetPersisted(sequenceNumberMap);
        if (persistedSequenceNumberMap != null)
            sequenceNumberMap.setHashMap(getPurgedSequenceNumberMap(persistedSequenceNumberMap.getHashMap()));

        final String storageFileName = "PersistedP2PStorageData";

        File dbDir = new File(storageDir.getAbsolutePath());
        if (!dbDir.exists() && !dbDir.mkdir())
            log.warn("make dir failed.\ndbDir=" + dbDir.getAbsolutePath());

        final File destinationFile = new File(Paths.get(storageDir.getAbsolutePath(), storageFileName).toString());
        if (!destinationFile.exists()) {
            try {
                FileUtil.resourceToFile(storageFileName, destinationFile);
            } catch (ResourceNotFoundException | IOException e) {
                //e.printStackTrace();
                log.error("Could not copy the " + storageFileName + " resource file to the db directory.\n" + e.getMessage());
            }
        } else {
            log.debug(storageFileName + " file exists already.");
        }

        PersistableHashMap<ByteArray, ProtectedStorageEntry> persisted = persistedEntryMapStorage.<HashMap<ByteArray, MapValue>>initAndGetPersistedWithFileName(storageFileName);
        if (persisted != null) {
            persistedMap = persisted.getHashMap();
            map.putAll(persistedMap);

            // In case another object is already listening...
            map.values().stream()
                    .forEach(protectedStorageEntry -> hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedStorageEntry)));
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Protobuffer
    ///////////////////////////////////////////////////////////////////////////////////////////

    public Message toProtoMessage() {
        return toProtoMessageFunction().apply(persistedMap);
    }

    @NotNull
    /** convert hashmap to proto representation */
    static Function<HashMap<ByteArray, ProtectedStorageEntry>, Message> toProtoMessageFunction() {
        return (HashMap<ByteArray, ProtectedStorageEntry> map) -> {
            Map<String, PB.ProtectedStorageEntry> protoResult =
                    map.entrySet().stream()
                            .collect(Collectors.toMap(
                                            e -> e.getKey().toString(),
                                            e -> (PB.ProtectedStorageEntry) e.getValue().toProtoMessage())
                            );
            return PB.PersistableEnvelope.newBuilder().setPersistedEntryMap(PB.PersistedEntryMap.newBuilder().putAllPersistedEntryMap(protoResult)).build();
        };
    }

    public static PersistableEnvelope fromProto(Map<String, PB.ProtectedStorageEntry> protoResult,
                                                NetworkProtoResolver networkProtoResolver) {
        Map<ByteArray, ProtectedStorageEntry> result = protoResult.entrySet().stream()
                .collect(Collectors.<Entry<String, PB.ProtectedStorageEntry>, ByteArray, ProtectedStorageEntry>toMap(
                        e -> new ByteArray(e.getKey().getBytes()),
                        e -> ProtectedStorageEntry.fromProto(e.getValue(), networkProtoResolver)
                ));
        return new PersistableHashMap<>(new HashMap<>(result), P2PDataStorage.toProtoMessageFunction());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (removeExpiredEntriesTimer != null)
            removeExpiredEntriesTimer.stop();
    }

    public void onBootstrapComplete() {
        removeExpiredEntriesTimer = UserThread.runPeriodically(() -> {
            log.trace("removeExpiredEntries");
            // The moment when an object becomes expired will not be synchronous in the network and we could
            // get add network_messages after the object has expired. To avoid repeated additions of already expired
            // object when we get it sent from new peers, we don’t remove the sequence number from the map.
            // That way an ADD message for an already expired data will fail because the sequence number
            // is equal and not larger as expected.
            Map<ByteArray, ProtectedStorageEntry> temp = new HashMap<>(map);
            Set<ProtectedStorageEntry> toRemoveSet = new HashSet<>();
            temp.entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired())
                    .forEach(entry -> {
                        ByteArray hashOfPayload = entry.getKey();
                        ProtectedStorageEntry protectedStorageEntry = map.get(hashOfPayload);
                        if (!(protectedStorageEntry.getStoragePayload() instanceof PersistedStoragePayload)) {
                            toRemoveSet.add(protectedStorageEntry);
                            log.debug("We found an expired data entry. We remove the protectedData:\n\t" + Utilities.toTruncatedString(protectedStorageEntry));
                            map.remove(hashOfPayload);
                        }
                    });

            toRemoveSet.stream().forEach(
                    protectedDataToRemove -> hashMapChangedListeners.stream().forEach(
                            listener -> listener.onRemoved(protectedDataToRemove)));

            if (sequenceNumberMap.size() > 1000)
                sequenceNumberMap.setHashMap(getPurgedSequenceNumberMap(sequenceNumberMap.getHashMap()));
        }, CHECK_TTL_INTERVAL_SEC);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope wireEnvelope, Connection connection) {
        if (wireEnvelope instanceof BroadcastMessage) {
            Log.traceCall(Utilities.toTruncatedString(wireEnvelope) + "\n\tconnection=" + connection);
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (wireEnvelope instanceof AddDataMessage) {
                    add(((AddDataMessage) wireEnvelope).getProtectedStorageEntry(), peersNodeAddress, null, false);
                } else if (wireEnvelope instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) wireEnvelope).getProtectedStorageEntry(), peersNodeAddress, false);
                } else if (wireEnvelope instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) wireEnvelope).getProtectedMailboxStorageEntry(), peersNodeAddress, false);
                } else if (wireEnvelope instanceof RefreshOfferMessage) {
                    refreshTTL((RefreshOfferMessage) wireEnvelope, peersNodeAddress, false);
                }
            });
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // ConnectionListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onConnection(Connection connection) {
    }

    @Override
    public void onDisconnect(CloseConnectionReason closeConnectionReason, Connection connection) {
        if (connection.hasPeersNodeAddress() && !closeConnectionReason.isIntended) {
            map.values().stream()
                    .forEach(protectedData -> {
                        ExpirablePayload expirablePayload = protectedData.getStoragePayload();
                        if (expirablePayload instanceof RequiresOwnerIsOnlinePayload) {
                            RequiresOwnerIsOnlinePayload requiresOwnerIsOnlinePayload = (RequiresOwnerIsOnlinePayload) expirablePayload;
                            NodeAddress ownerNodeAddress = requiresOwnerIsOnlinePayload.getOwnerNodeAddress();
                            if (ownerNodeAddress.equals(connection.getPeersNodeAddressOptional().get())) {
                                // We have a RequiresLiveOwnerData data object with the node address of the
                                // disconnected peer. We remove that data from our map.

                                // Check if we have the data (e.g. OfferPayload)
                                ByteArray hashOfPayload = getHashAsByteArray(expirablePayload);
                                boolean containsKey = map.containsKey(hashOfPayload);
                                if (containsKey) {
                                    log.debug("We remove the data as the data owner got disconnected with " +
                                            "closeConnectionReason=" + closeConnectionReason);

                                    //noinspection ConstantConditions
                                    Log.logIfStressTests("We remove the data as the data owner got disconnected with " +
                                            "closeConnectionReason=" + closeConnectionReason +
                                            " / isIntended=" + closeConnectionReason.isIntended +
                                            " / peer=" + (connection.getPeersNodeAddressOptional().isPresent() ? connection.getPeersNodeAddressOptional().get() : "PeersNode unknown"));

                                    // We only set the data back by half of the TTL and remove the data only if is has
                                    // expired after tha back dating.
                                    // We might get connection drops which are not caused by the node going offline, so
                                    // we give more tolerance with that approach, giving the node the change to
                                    // refresh the TTL with a refresh message.
                                    // We observed those issues during stress tests, but it might have been caused by the
                                    // test set up (many nodes/connections over 1 router)
                                    // TODO investigate what causes the disconnections.
                                    // Usually the are: SOCKET_TIMEOUT ,TERMINATED (EOFException)
                                    protectedData.backDate();
                                    if (protectedData.isExpired())
                                        doRemoveProtectedExpirableData(protectedData, hashOfPayload);
                                } else {
                                    log.debug("Remove data ignored as we don't have an entry for that data.");
                                }
                            }
                        }
                    });
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public boolean add(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender,
                       @Nullable BroadcastHandler.Listener listener, boolean isDataOwner) {
        Log.traceCall("with allowBroadcast=true");
        return add(protectedStorageEntry, sender, listener, isDataOwner, true);
    }

    public boolean add(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender,
                       @Nullable BroadcastHandler.Listener listener, boolean isDataOwner, boolean allowBroadcast) {
        Log.traceCall("with allowBroadcast=" + allowBroadcast);

        final StoragePayload storagePayload = protectedStorageEntry.getStoragePayload();
        ByteArray hashOfPayload = getHashAsByteArray(storagePayload);
        boolean sequenceNrValid = isSequenceNrValid(protectedStorageEntry.getSequenceNumber(), hashOfPayload);
        boolean result = checkPublicKeys(protectedStorageEntry, true)
                && checkSignature(protectedStorageEntry)
                && sequenceNrValid;

        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey)
            result = result && checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedStorageEntry.getOwnerPubKey(), hashOfPayload);

        // printData("before add");
        if (result) {
            final boolean hasSequenceNrIncreased = hasSequenceNrIncreased(protectedStorageEntry.getSequenceNumber(), hashOfPayload);
            if (!containsKey || hasSequenceNrIncreased) {
                // At startup we don't have the item so we store it. At updates of the seq nr we store as well.
                map.put(hashOfPayload, protectedStorageEntry);

                // If we get a PersistedStoragePayload we save to disc
                if (storagePayload instanceof PersistedStoragePayload) {
                    persistedMap.put(hashOfPayload, protectedStorageEntry);
                    persistedEntryMapStorage.queueUpForSave(new PersistableHashMap<>(persistedMap, toProtoMessageFunction()), 5000);
                }

                hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedStorageEntry));
                // printData("after add");
            } else {
                log.trace("We got that version of the data already, so we don't store it.");
            }

            if (hasSequenceNrIncreased) {
                sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.getSequenceNumber(), System.currentTimeMillis()));
                // We set the delay higher as we might receive a batch of items
                sequenceNumberMapStorage.queueUpForSave(new SequenceNumberMap(sequenceNumberMap), 2000);

                if (allowBroadcast)
                    broadcast(new AddDataMessage(protectedStorageEntry), sender, listener, isDataOwner);
            } else {
                log.trace("We got that version of the data already, so we don't broadcast it.");
            }
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public boolean refreshTTL(RefreshOfferMessage refreshTTLMessage, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();

        byte[] hashOfDataAndSeqNr = refreshTTLMessage.getHashOfDataAndSeqNr();
        byte[] signature = refreshTTLMessage.getSignature();
        ByteArray hashOfPayload = new ByteArray(refreshTTLMessage.getHashOfPayload());
        int sequenceNumber = refreshTTLMessage.getSequenceNumber();

        if (map.containsKey(hashOfPayload)) {
            ProtectedStorageEntry storedData = map.get(hashOfPayload);

            if (sequenceNumberMap.containsKey(hashOfPayload) && sequenceNumberMap.get(hashOfPayload).sequenceNr == sequenceNumber) {
                log.trace("We got that message with that seq nr already from another peer. We ignore that message.");
                return true;
            } else {
                PublicKey ownerPubKey = storedData.getStoragePayload().getOwnerPubKey();
                final boolean checkSignature = checkSignature(ownerPubKey, hashOfDataAndSeqNr, signature);
                final boolean hasSequenceNrIncreased = hasSequenceNrIncreased(sequenceNumber, hashOfPayload);
                final boolean checkIfStoredDataPubKeyMatchesNewDataPubKey = checkIfStoredDataPubKeyMatchesNewDataPubKey(ownerPubKey,
                        hashOfPayload);
                boolean allValid = checkSignature &&
                        hasSequenceNrIncreased &&
                        checkIfStoredDataPubKeyMatchesNewDataPubKey;

                // printData("before refreshTTL");
                if (allValid) {
                    log.debug("refreshDate called for storedData:\n\t" + StringUtils.abbreviate(storedData.toString(), 100));
                    storedData.refreshTTL();
                    storedData.updateSequenceNumber(sequenceNumber);
                    storedData.updateSignature(signature);
                    printData("after refreshTTL");
                    sequenceNumberMap.put(hashOfPayload, new MapValue(sequenceNumber, System.currentTimeMillis()));
                    sequenceNumberMapStorage.queueUpForSave(new SequenceNumberMap(sequenceNumberMap), 1000);

                    broadcast(refreshTTLMessage, sender, null, isDataOwner);
                }
                return allValid;
            }
        } else {
            log.debug("We don't have data for that refresh message in our map. That is expected if we missed the data publishing.");
            return false;
        }
    }

    public boolean remove(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(protectedStorageEntry.getStoragePayload());
        boolean containsKey = map.containsKey(hashOfPayload);
        if (!containsKey)
            log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedStorageEntry, false)
                && isSequenceNrValid(protectedStorageEntry.getSequenceNumber(), hashOfPayload)
                && checkSignature(protectedStorageEntry)
                && checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedStorageEntry.getOwnerPubKey(), hashOfPayload);

        // printData("before remove");
        if (result) {
            doRemoveProtectedExpirableData(protectedStorageEntry, hashOfPayload);
            printData("after remove");
            sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.getSequenceNumber(), System.currentTimeMillis()));
            sequenceNumberMapStorage.queueUpForSave(new SequenceNumberMap(sequenceNumberMap), 300);

            broadcast(new RemoveDataMessage(protectedStorageEntry), sender, null, isDataOwner);
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean removeMailboxData(ProtectedMailboxStorageEntry protectedMailboxStorageEntry, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(protectedMailboxStorageEntry.getStoragePayload());
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey)
            log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxStorageEntry, false)
                && isSequenceNrValid(protectedMailboxStorageEntry.getSequenceNumber(), hashOfData)
                && protectedMailboxStorageEntry.getMailboxStoragePayload().getOwnerPubKey().equals(protectedMailboxStorageEntry.getReceiversPubKey()) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxStorageEntry)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxStorageEntry.getReceiversPubKey(), hashOfData);

        // printData("before removeMailboxData");
        if (result) {
            doRemoveProtectedExpirableData(protectedMailboxStorageEntry, hashOfData);
            printData("after removeMailboxData");
            sequenceNumberMap.put(hashOfData, new MapValue(protectedMailboxStorageEntry.getSequenceNumber(), System.currentTimeMillis()));
            sequenceNumberMapStorage.queueUpForSave(new SequenceNumberMap(sequenceNumberMap), 300);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxStorageEntry), sender, null, isDataOwner);
        } else {
            log.debug("removeMailboxData failed");
        }
        return result;
    }


    public Map<ByteArray, ProtectedStorageEntry> getMap() {
        return map;
    }

    public ProtectedStorageEntry getProtectedData(StoragePayload storagePayload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfData = getHashAsByteArray(storagePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = EncryptionService.getHash(new DataAndSeqNrPair(storagePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedStorageEntry(storagePayload, ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public RefreshOfferMessage getRefreshTTLMessage(StoragePayload storagePayload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfPayload = getHashAsByteArray(storagePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfPayload))
            sequenceNumber = sequenceNumberMap.get(hashOfPayload).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = EncryptionService.getHash(new DataAndSeqNrPair(storagePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new RefreshOfferMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
    }

    public ProtectedMailboxStorageEntry getMailboxDataWithSignedSeqNr(MailboxStoragePayload expirableMailboxStoragePayload,
                                                                      KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        ByteArray hashOfData = getHashAsByteArray(expirableMailboxStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = EncryptionService.getHash(new DataAndSeqNrPair(expirableMailboxStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(storageSignaturePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedMailboxStorageEntry(expirableMailboxStoragePayload,
                storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey);
    }

    public void addHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        hashMapChangedListeners.add(hashMapChangedListener);
    }

    public void removeHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        hashMapChangedListeners.remove(hashMapChangedListener);
    }

    public Set<ProtectedStorageEntry> getFilteredValues(Set<ByteArray> excludedKeys) {
        return map.entrySet()
                .stream().filter(e -> !excludedKeys.contains(e.getKey()))
                .map(Entry::getValue)
                .collect(Collectors.toSet());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doRemoveProtectedExpirableData(ProtectedStorageEntry protectedStorageEntry, ByteArray hashOfPayload) {
        map.remove(hashOfPayload);
        log.trace("Data removed from our map. We broadcast the message to our peers.");
        hashMapChangedListeners.stream().forEach(e -> e.onRemoved(protectedStorageEntry));
    }

    private boolean isSequenceNrValid(int newSequenceNumber, ByteArray hashOfData) {
        if (sequenceNumberMap.containsKey(hashOfData)) {
            int storedSequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr;
            if (newSequenceNumber >= storedSequenceNumber) {
                log.trace("Sequence number is valid (>=). sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
                return true;
            } else {
                log.debug("Sequence number is invalid. sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber + "\n" +
                        "That can happen if the data owner gets an old delayed data storage message.");
                return false;
            }
        } else {
            log.trace("Sequence number is valid (!sequenceNumberMap.containsKey(hashOfData)). sequenceNumber = " + newSequenceNumber);
            return true;
        }
    }

    private boolean hasSequenceNrIncreased(int newSequenceNumber, ByteArray hashOfData) {
        if (sequenceNumberMap.containsKey(hashOfData)) {
            int storedSequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr;
            if (newSequenceNumber > storedSequenceNumber) {
                log.trace("Sequence number has increased (>). sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber + " / hashOfData=" + hashOfData.toString());
                return true;
            } else if (newSequenceNumber == storedSequenceNumber) {
                String msg;
                if (newSequenceNumber == 0) {
                    msg = "Sequence number is equal to the stored one and both are 0." +
                            "That is expected for network_messages which never got updated (mailbox msg).";
                } else {
                    msg = "Sequence number is equal to the stored one. sequenceNumber = "
                            + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber;
                }
                log.debug(msg);
                return false;
            } else {
                log.debug("Sequence number is invalid. sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber + "\n" +
                        "That can happen if the data owner gets an old delayed data storage message.");
                return false;
            }
        } else {
            log.trace("Sequence number has increased (!sequenceNumberMap.containsKey(hashOfData)). sequenceNumber = " + newSequenceNumber + " / hashOfData=" + hashOfData.toString());
            return true;
        }
    }

    boolean checkSignature(PublicKey ownerPubKey, byte[] hashOfDataAndSeqNr, byte[] signature) {
        try {
            boolean result = Sig.verify(ownerPubKey, hashOfDataAndSeqNr, signature);
            if (!result)
                log.warn("Signature verification failed at checkSignature. " +
                        "That should not happen.");

            return result;
        } catch (CryptoException e) {
            log.error("Signature verification failed at checkSignature");
            return false;
        }
    }

    private boolean checkSignature(ProtectedStorageEntry protectedStorageEntry) {
        byte[] hashOfDataAndSeqNr = EncryptionService.getHash(new DataAndSeqNrPair(protectedStorageEntry.getStoragePayload(), protectedStorageEntry.getSequenceNumber()));
        return checkSignature(protectedStorageEntry.getOwnerPubKey(), hashOfDataAndSeqNr, protectedStorageEntry.getSignature());
    }

    // Check that the pubkey of the storage entry matches the allowed pubkey for the addition or removal operation
    // in the contained mailbox message, or the pubKey of other kinds of network_messages.
    boolean checkPublicKeys(ProtectedStorageEntry protectedStorageEntry, boolean isAddOperation) {
        boolean result;
        if (protectedStorageEntry.getStoragePayload() instanceof MailboxStoragePayload) {
            MailboxStoragePayload payload = (MailboxStoragePayload) protectedStorageEntry.getStoragePayload();
            if (isAddOperation)
                result = payload.getSenderPubKeyForAddOperation() != null &&
                        payload.getSenderPubKeyForAddOperation().equals(protectedStorageEntry.getOwnerPubKey());
            else
                result = payload.getOwnerPubKey() != null &&
                        payload.getOwnerPubKey().equals(protectedStorageEntry.getOwnerPubKey());
        } else {
            // TODO We got sometimes a nullPointer at protectedStorageEntry.ownerPubKey
            // Probably caused by an exception at deserialization:  OfferPayload: Cannot be deserialized.null
            result = protectedStorageEntry.getOwnerPubKey() != null &&
                    protectedStorageEntry.getStoragePayload() != null &&
                    protectedStorageEntry.getOwnerPubKey().equals(protectedStorageEntry.getStoragePayload().getOwnerPubKey());
        }

        if (!result) {
            String res1 = protectedStorageEntry.toString();
            String res2 = "null";
            if (protectedStorageEntry.getStoragePayload() != null &&
                    protectedStorageEntry.getStoragePayload().getOwnerPubKey() != null)
                res2 = protectedStorageEntry.getStoragePayload().getOwnerPubKey().toString();

            log.warn("PublicKey of payload data and ProtectedData are not matching. protectedStorageEntry=" + res1 +
                    "protectedStorageEntry.getStoragePayload().getOwnerPubKey()=" + res2);
        }
        return result;
    }

    private boolean checkIfStoredDataPubKeyMatchesNewDataPubKey(PublicKey ownerPubKey, ByteArray hashOfData) {
        ProtectedStorageEntry storedData = map.get(hashOfData);
        boolean result = storedData.getOwnerPubKey() != null && storedData.getOwnerPubKey().equals(ownerPubKey);
        if (!result)
            log.warn("New data entry does not match our stored data. storedData.ownerPubKey=" +
                    (storedData.getOwnerPubKey() != null ? storedData.getOwnerPubKey().toString() : "null") +
                    ", ownerPubKey=" + ownerPubKey);

        return result;
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(PublicKey receiversPubKey, ByteArray hashOfData) {
        ProtectedStorageEntry storedData = map.get(hashOfData);
        if (storedData instanceof ProtectedMailboxStorageEntry) {
            ProtectedMailboxStorageEntry entry = (ProtectedMailboxStorageEntry) storedData;
            // publicKey is not the same (stored: sender, new: receiver)
            boolean result = entry.getReceiversPubKey().equals(receiversPubKey)
                    && getHashAsByteArray(entry.getStoragePayload()).equals(hashOfData);
            if (!result)
                log.warn("New data entry does not match our stored data. entry.receiversPubKey=" + entry.getReceiversPubKey()
                        + ", receiversPubKey=" + receiversPubKey);

            return result;
        } else {
            log.error("We expected a MailboxData but got other type. That must never happen. storedData=" + storedData);
            return false;
        }
    }

    private void broadcast(BroadcastMessage message, @Nullable NodeAddress sender,
                           @Nullable BroadcastHandler.Listener listener, boolean isDataOwner) {
        broadcaster.broadcast(message, sender, listener, isDataOwner);
    }

    private ByteArray getHashAsByteArray(ExpirablePayload data) {
        return new ByteArray(EncryptionService.getHash(data));
    }

    // Get a new map with entries older than PURGE_AGE_DAYS purged from the given map.
    private HashMap<ByteArray, MapValue> getPurgedSequenceNumberMap(HashMap<ByteArray, MapValue> persisted) {
        HashMap<ByteArray, MapValue> purged = new HashMap<>();
        long maxAgeTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(PURGE_AGE_DAYS);
        persisted.entrySet().stream().forEach(entry -> {
            if (entry.getValue().timeStamp > maxAgeTs)
                purged.put(entry.getKey(), entry.getValue());
        });
        return purged;
    }

    private void printData(String info) {
        if (LoggerFactory.getLogger(Log.class).isInfoEnabled() || LoggerFactory.getLogger(Log.class).isDebugEnabled()) {
            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set ").append(info).append(" operation");
            // We print the items sorted by hash with the payload class name and id
            List<Tuple2<String, ProtectedStorageEntry>> tempList = map.values().stream()
                    .map(e -> new Tuple2<>(org.bitcoinj.core.Utils.HEX.encode(getHashAsByteArray(e.getStoragePayload()).bytes), e))
                    .collect(Collectors.toList());
            tempList.sort((o1, o2) -> o1.first.compareTo(o2.first));
            tempList.stream().forEach(e -> {
                final ProtectedStorageEntry storageEntry = e.second;
                final StoragePayload storagePayload = storageEntry.getStoragePayload();
                final MapValue mapValue = sequenceNumberMap.get(getHashAsByteArray(storagePayload));
                sb.append("\n")
                        .append("Hash=")
                        .append(e.first)
                        .append("; Class=")
                        .append(storagePayload.getClass().getSimpleName())
                        .append("; SequenceNumbers (Object/Stored)=")
                        .append(storageEntry.getSequenceNumber())
                        .append(" / ")
                        .append(mapValue != null ? mapValue.sequenceNr : "null")
                        .append("; TimeStamp (Object/Stored)=")
                        .append(storageEntry.getCreationTimeStamp())
                        .append(" / ")
                        .append(mapValue != null ? mapValue.timeStamp : "null")
                        .append("; Payload=")
                        .append(Utilities.toTruncatedString(storagePayload));
            });
            sb.append("\n------------------------------------------------------------\n");
            log.debug(sb.toString());
            log.debug("Data set " + info + " operation: size=" + map.values().size());
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static class
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Used as container for calculating cryptographic hash of data and sequenceNumber.
     * Needs to be Serializable because we convert the object to a byte array via java serialization
     * before calculating the hash.
     */
    public static final class DataAndSeqNrPair implements NetworkPayload {
        // data are only used for calculating cryptographic hash from both values so they are kept private
        private final StoragePayload data;
        private final int sequenceNumber;

        public DataAndSeqNrPair(StoragePayload data, int sequenceNumber) {
            this.data = data;
            this.sequenceNumber = sequenceNumber;
        }

        @Override
        public String toString() {
            return "DataAndSeqNr{" +
                    "data=" + data +
                    ", sequenceNumber=" + sequenceNumber +
                    '}';
        }

        @Override
        public com.google.protobuf.Message toProtoMessage() {
            return PB.DataAndSeqNrPair.newBuilder().setPayload((PB.StoragePayload) data.toProtoMessage()).setSequenceNumber(sequenceNumber).build();
        }
    }


    /**
     * Used as key object in map for cryptographic hash of stored data as byte[] as primitive data type cannot be
     * used as key
     */
    @EqualsAndHashCode
    @AllArgsConstructor
    public static final class ByteArray implements PersistablePayload {
        // That object is saved to disc. We need to take care of changes to not break deserialization.
        public final byte[] bytes;

        @Override
        public String toString() {
            return "ByteArray{" +
                    "bytes as Hex=" + Hex.toHexString(bytes) +
                    '}';
        }

        @Override
        public PB.ByteArray toProtoMessage() {
            return PB.ByteArray.newBuilder().setBytes(ByteString.copyFrom(bytes)).build();
        }

        public static ByteArray fromProto(PB.ByteArray proto) {
            return new ByteArray(proto.getBytes().toByteArray());
        }
    }

    /**
     * Used as value in map
     */
    @EqualsAndHashCode
    @ToString
    public static final class MapValue implements PersistablePayload {
        // That object is saved to disc. We need to take care of changes to not break deserialization.
        final public int sequenceNr;
        final public long timeStamp;

        public MapValue(int sequenceNr, long timeStamp) {
            this.sequenceNr = sequenceNr;
            this.timeStamp = timeStamp;
        }

        @Override
        public PB.MapValue toProtoMessage() {
            return PB.MapValue.newBuilder().setSequenceNr(sequenceNr).setTimeStamp(timeStamp).build();
        }

        public static MapValue fromProto(PB.MapValue proto) {
            return new MapValue(proto.getSequenceNr(), proto.getTimeStamp());
        }
    }
}

