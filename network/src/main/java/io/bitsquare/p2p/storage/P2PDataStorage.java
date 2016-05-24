package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.Timer;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.BroadcastHandler;
import io.bitsquare.p2p.peers.Broadcaster;
import io.bitsquare.p2p.storage.messages.*;
import io.bitsquare.p2p.storage.payload.ExpirablePayload;
import io.bitsquare.p2p.storage.payload.MailboxStoragePayload;
import io.bitsquare.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import io.bitsquare.p2p.storage.payload.StoragePayload;
import io.bitsquare.p2p.storage.storageentry.ProtectedMailboxStorageEntry;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;
import io.bitsquare.storage.Storage;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.io.Serializable;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;

// Run in UserThread
public class P2PDataStorage implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    /**
     * How many days to keep an entry before it is purged.
     */
    public static final int PURGE_AGE_DAYS = 10;

    @VisibleForTesting
    public static int CHECK_TTL_INTERVAL_SEC = Timer.STRESS_TEST ? 5 : 60;

    private final Broadcaster broadcaster;
    private final Map<ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private Timer removeExpiredEntriesTimer;
    private HashMap<ByteArray, MapValue> sequenceNumberMap = new HashMap<>();
    private final Storage<HashMap> storage;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PDataStorage(Broadcaster broadcaster, NetworkNode networkNode, File storageDir) {
        this.broadcaster = broadcaster;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        storage = new Storage<>(storageDir);

        HashMap<ByteArray, MapValue> persisted = storage.initAndGetPersisted("SequenceNumberMap");
        if (persisted != null)
            sequenceNumberMap = getPurgedSequenceNumberMap(persisted);
    }

    public void shutDown() {
        if (removeExpiredEntriesTimer != null)
            removeExpiredEntriesTimer.stop();
    }

    public void onBootstrapComplete() {
        removeExpiredEntriesTimer = UserThread.runPeriodically(() -> {
            log.trace("removeExpiredEntries");
            // The moment when an object becomes expired will not be synchronous in the network and we could 
            // get add messages after the object has expired. To avoid repeated additions of already expired 
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
                        toRemoveSet.add(protectedStorageEntry);
                        log.info("We found an expired data entry. We remove the protectedData:\n\t" + protectedStorageEntry);
                        map.remove(hashOfPayload);
                    });

            toRemoveSet.stream().forEach(
                    protectedDataToRemove -> hashMapChangedListeners.stream().forEach(
                            listener -> listener.onRemoved(protectedDataToRemove)));

            if (sequenceNumberMap.size() > 1000)
                sequenceNumberMap = getPurgedSequenceNumberMap(sequenceNumberMap);
        }, CHECK_TTL_INTERVAL_SEC);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof BroadcastMessage) {
            Log.traceCall(StringUtils.abbreviate(message.toString(), 100) + "\n\tconnection=" + connection);
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (message instanceof AddDataMessage) {
                    add(((AddDataMessage) message).protectedStorageEntry, peersNodeAddress, null, false);
                } else if (message instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) message).protectedStorageEntry, peersNodeAddress, false);
                } else if (message instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) message).protectedMailboxStorageEntry, peersNodeAddress, false);
                } else if (message instanceof RefreshTTLMessage) {
                    refreshTTL((RefreshTTLMessage) message, peersNodeAddress, false);
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

                                // Check if we have the data (e.g. Offer)
                                ByteArray hashOfPayload = getHashAsByteArray(expirablePayload);
                                boolean containsKey = map.containsKey(hashOfPayload);
                                if (containsKey) {
                                    log.info("We remove the data as the data owner got disconnected with " +
                                            "closeConnectionReason=" + closeConnectionReason);
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
        Log.traceCall();

        ByteArray hashOfPayload = getHashAsByteArray(protectedStorageEntry.getStoragePayload());
        boolean sequenceNrValid = isSequenceNrValid(protectedStorageEntry.sequenceNumber, hashOfPayload);
        boolean result = checkPublicKeys(protectedStorageEntry, true)
                && checkSignature(protectedStorageEntry)
                && sequenceNrValid;

        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey)
            result &= checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedStorageEntry.ownerPubKey, hashOfPayload);

        if (result) {
            map.put(hashOfPayload, protectedStorageEntry);

            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set after doAdd (truncated)");
            map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
            sb.append("\n------------------------------------------------------------\n");
            log.trace(sb.toString());
            log.info("Data set after doAdd: size=" + map.values().size());

            if (hasSequenceNrIncreased(protectedStorageEntry.sequenceNumber, hashOfPayload)) {
                sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.sequenceNumber, System.currentTimeMillis()));
                storage.queueUpForSave(sequenceNumberMap, 100);

                broadcast(new AddDataMessage(protectedStorageEntry), sender, listener, isDataOwner);
            }

            hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedStorageEntry));
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public boolean refreshTTL(RefreshTTLMessage refreshTTLMessage, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();

        byte[] hashOfDataAndSeqNr = refreshTTLMessage.hashOfDataAndSeqNr;
        byte[] signature = refreshTTLMessage.signature;
        ByteArray hashOfPayload = new ByteArray(refreshTTLMessage.hashOfPayload);
        int sequenceNumber = refreshTTLMessage.sequenceNumber;

        if (map.containsKey(hashOfPayload)) {
            ProtectedStorageEntry storedData = map.get(hashOfPayload);

            if (sequenceNumberMap.containsKey(hashOfPayload) && sequenceNumberMap.get(hashOfPayload).sequenceNr == sequenceNumber) {
                log.trace("We got that message with that seq nr already from another peer. We ignore that message.");
                return true;
            } else {
                PublicKey ownerPubKey = storedData.getStoragePayload().getOwnerPubKey();
                boolean result = checkSignature(ownerPubKey, hashOfDataAndSeqNr, signature) &&
                        hasSequenceNrIncreased(sequenceNumber, hashOfPayload) &&
                        checkIfStoredDataPubKeyMatchesNewDataPubKey(ownerPubKey, hashOfPayload);

                if (result) {
                    log.info("refreshDate called for storedData:\n\t" + StringUtils.abbreviate(storedData.toString(), 100));
                    storedData.refreshTTL();
                    storedData.updateSequenceNumber(sequenceNumber);
                    storedData.updateSignature(signature);

                    sequenceNumberMap.put(hashOfPayload, new MapValue(sequenceNumber, System.currentTimeMillis()));
                    storage.queueUpForSave(sequenceNumberMap, 100);

                    StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
                    sb.append("Data set after refreshTTL (truncated)");
                    map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
                    sb.append("\n------------------------------------------------------------\n");
                    log.trace(sb.toString());
                    log.info("Data set after refreshTTL: size=" + map.values().size());

                    broadcast(refreshTTLMessage, sender, null, isDataOwner);
                }
                return result;
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
                && isSequenceNrValid(protectedStorageEntry.sequenceNumber, hashOfPayload)
                && checkSignature(protectedStorageEntry)
                && checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedStorageEntry.ownerPubKey, hashOfPayload);


        if (result) {
            doRemoveProtectedExpirableData(protectedStorageEntry, hashOfPayload);

            broadcast(new RemoveDataMessage(protectedStorageEntry), sender, null, isDataOwner);

            sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.sequenceNumber, System.currentTimeMillis()));
            storage.queueUpForSave(sequenceNumberMap, 100);
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    public boolean removeMailboxData(ProtectedMailboxStorageEntry protectedMailboxStorageEntry, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(protectedMailboxStorageEntry.getStoragePayload());
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey)
            log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxStorageEntry, false)
                && isSequenceNrValid(protectedMailboxStorageEntry.sequenceNumber, hashOfData)
                && protectedMailboxStorageEntry.getMailboxStoragePayload().receiverPubKeyForRemoveOperation.equals(protectedMailboxStorageEntry.receiversPubKey) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxStorageEntry)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxStorageEntry.receiversPubKey, hashOfData);

        if (result) {
            doRemoveProtectedExpirableData(protectedMailboxStorageEntry, hashOfData);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxStorageEntry), sender, null, isDataOwner);

            sequenceNumberMap.put(hashOfData, new MapValue(protectedMailboxStorageEntry.sequenceNumber, System.currentTimeMillis()));
            storage.queueUpForSave(sequenceNumberMap, 100);
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
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(storagePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedStorageEntry(storagePayload, ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public RefreshTTLMessage getRefreshTTLMessage(StoragePayload storagePayload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfPayload = getHashAsByteArray(storagePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfPayload))
            sequenceNumber = sequenceNumberMap.get(hashOfPayload).sequenceNr + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(storagePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new RefreshTTLMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
    }

    public ProtectedMailboxStorageEntry getMailboxDataWithSignedSeqNr(MailboxStoragePayload expirableMailboxStoragePayload,
                                                                      KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        ByteArray hashOfData = getHashAsByteArray(expirableMailboxStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(expirableMailboxStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(storageSignaturePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedMailboxStorageEntry(expirableMailboxStoragePayload,
                storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey);
    }

    public void addHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        hashMapChangedListeners.add(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doRemoveProtectedExpirableData(ProtectedStorageEntry protectedStorageEntry, ByteArray hashOfPayload) {
        map.remove(hashOfPayload);
        log.trace("Data removed from our map. We broadcast the message to our peers.");
        hashMapChangedListeners.stream().forEach(e -> e.onRemoved(protectedStorageEntry));

        StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Data set after removeProtectedExpirableData: (truncated)");
        map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
        sb.append("\n------------------------------------------------------------\n");
        log.trace(sb.toString());
        log.info("Data set after doRemoveProtectedExpirableData: size=" + map.values().size());
    }

    private boolean isSequenceNrValid(int newSequenceNumber, ByteArray hashOfData) {
        if (sequenceNumberMap.containsKey(hashOfData)) {
            int storedSequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr;
            if (newSequenceNumber >= storedSequenceNumber) {
                return true;
            } else {
                log.debug("Sequence number is invalid. sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber + "\n" +
                        "That can happen if the data owner gets an old delayed data storage message.");
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean hasSequenceNrIncreased(int newSequenceNumber, ByteArray hashOfData) {
        if (sequenceNumberMap.containsKey(hashOfData)) {
            int storedSequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr;
            if (newSequenceNumber > storedSequenceNumber) {
                return true;
            } else if (newSequenceNumber == storedSequenceNumber) {
                if (newSequenceNumber == 0) {
                    log.debug("Sequence number is equal to the stored one and both are 0." +
                            "That is expected for messages which never got updated (mailbox msg).");
                    return false;
                } else {
                    log.debug("Sequence number is equal to the stored one. sequenceNumber = "
                            + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
                    return false;
                }
            } else {
                log.debug("Sequence number is invalid. sequenceNumber = "
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber + "\n" +
                        "That can happen if the data owner gets an old delayed data storage message.");
                return false;
            }
        } else {
            return true;
        }
    }

    private boolean checkSignature(PublicKey ownerPubKey, byte[] hashOfDataAndSeqNr, byte[] signature) {
        try {
            boolean result = Sig.verify(ownerPubKey, hashOfDataAndSeqNr, signature);
            if (!result)
                log.error("Signature verification failed at checkSignature. " +
                        "That should not happen. Consider it might be an attempt of fraud.");

            return result;
        } catch (CryptoException e) {
            log.error("Signature verification failed at checkSignature");
            return false;
        }
    }

    private boolean checkSignature(ProtectedStorageEntry protectedStorageEntry) {
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(protectedStorageEntry.getStoragePayload(), protectedStorageEntry.sequenceNumber));
        return checkSignature(protectedStorageEntry.ownerPubKey, hashOfDataAndSeqNr, protectedStorageEntry.signature);
    }

    // Check that the pubkey of the storage entry matches the allowed pubkey for the addition or removal operation
    // in the contained mailbox message, or the pubkey of other kinds of messages.
    private boolean checkPublicKeys(ProtectedStorageEntry protectedStorageEntry, boolean isAddOperation) {
        boolean result;
        if (protectedStorageEntry.getStoragePayload() instanceof MailboxStoragePayload) {
            MailboxStoragePayload expirableMailboxStoragePayload = (MailboxStoragePayload) protectedStorageEntry.getStoragePayload();
            if (isAddOperation)
                result = expirableMailboxStoragePayload.senderPubKeyForAddOperation.equals(protectedStorageEntry.ownerPubKey);
            else
                result = expirableMailboxStoragePayload.receiverPubKeyForRemoveOperation.equals(protectedStorageEntry.ownerPubKey);
        } else {
            // TODO We got sometimes a nullpointer at protectedStorageEntry.ownerPubKey
            // Probably caused by an exception at deserialization:  Offer: Cannot be deserialized.null 
            result = protectedStorageEntry != null && protectedStorageEntry.ownerPubKey != null &&
                    protectedStorageEntry.getStoragePayload() != null &&
                    protectedStorageEntry.ownerPubKey.equals(protectedStorageEntry.getStoragePayload().getOwnerPubKey());
        }

        if (!result)
            log.error("PublicKey of payload data and ProtectedData are not matching. Consider it might be an attempt of fraud");
        return result;
    }

    private boolean checkIfStoredDataPubKeyMatchesNewDataPubKey(PublicKey ownerPubKey, ByteArray hashOfData) {
        ProtectedStorageEntry storedData = map.get(hashOfData);
        boolean result = storedData.ownerPubKey.equals(ownerPubKey);
        if (!result)
            log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

        return result;
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(PublicKey receiversPubKey, ByteArray hashOfData) {
        ProtectedStorageEntry storedData = map.get(hashOfData);
        if (storedData instanceof ProtectedMailboxStorageEntry) {
            ProtectedMailboxStorageEntry storedMailboxData = (ProtectedMailboxStorageEntry) storedData;
            // publicKey is not the same (stored: sender, new: receiver)
            boolean result = storedMailboxData.receiversPubKey.equals(receiversPubKey)
                    && getHashAsByteArray(storedMailboxData.getStoragePayload()).equals(hashOfData);
            if (!result)
                log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

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
        return new ByteArray(Hash.getHash(data));
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


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static class
    ///////////////////////////////////////////////////////////////////////////////////////////


    /**
     * Used as container for calculating cryptographic hash of data and sequenceNumber.
     * Needs to be Serializable because we convert the object to a byte array via java serialization
     * before calculating the hash.
     */
    public static final class DataAndSeqNrPair implements Serializable {
        // data are only used for calculating cryptographic hash from both values so they are kept private
        private final Payload data;
        private final int sequenceNumber;

        public DataAndSeqNrPair(Payload data, int sequenceNumber) {
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
    }


    /**
     * Used as key object in map for cryptographic hash of stored data as byte[] as primitive data type cannot be
     * used as key
     */
    public static final class ByteArray implements Persistable {
        // That object is saved to disc. We need to take care of changes to not break deserialization.
        private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

        public final byte[] bytes;

        public ByteArray(byte[] bytes) {
            this.bytes = bytes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ByteArray)) return false;

            ByteArray byteArray = (ByteArray) o;

            return Arrays.equals(bytes, byteArray.bytes);
        }

        @Override
        public int hashCode() {
            return bytes != null ? Arrays.hashCode(bytes) : 0;
        }
    }


    /**
     * Used as value in map
     */
    private static final class MapValue implements Persistable {
        // That object is saved to disc. We need to take care of changes to not break deserialization.
        private static final long serialVersionUID = Version.LOCAL_DB_VERSION;

        final public int sequenceNr;
        final public long timeStamp;

        public MapValue(int sequenceNr, long timeStamp) {
            this.sequenceNr = sequenceNr;
            this.timeStamp = timeStamp;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof MapValue)) return false;

            MapValue mapValue = (MapValue) o;

            if (sequenceNr != mapValue.sequenceNr) return false;
            return timeStamp == mapValue.timeStamp;

        }

        @Override
        public int hashCode() {
            int result = sequenceNr;
            result = 31 * result + (int) (timeStamp ^ (timeStamp >>> 32));
            return result;
        }

        @Override
        public String toString() {
            return "MapValue{" +
                    "sequenceNr=" + sequenceNr +
                    ", timeStamp=" + timeStamp +
                    '}';
        }
    }
}
