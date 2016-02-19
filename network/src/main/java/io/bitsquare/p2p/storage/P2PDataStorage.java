package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import io.bitsquare.app.Log;
import io.bitsquare.app.Version;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.persistance.Persistable;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.common.wire.Payload;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.Broadcaster;
import io.bitsquare.p2p.storage.data.*;
import io.bitsquare.p2p.storage.messages.*;
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
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

// Run in UserThread
public class P2PDataStorage implements MessageListener, ConnectionListener {
    private static final Logger log = LoggerFactory.getLogger(P2PDataStorage.class);

    @VisibleForTesting
    //public static int CHECK_TTL_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(30);
    public static int CHECK_TTL_INTERVAL_MILLIS = (int) TimeUnit.SECONDS.toMillis(5);//TODO

    private final Broadcaster broadcaster;
    private final Map<ByteArray, ProtectedData> map = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private HashMap<ByteArray, MapValue> sequenceNumberMap = new HashMap<>();
    private final Storage<HashMap> storage;
    private final ScheduledThreadPoolExecutor removeExpiredEntriesExecutor;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public P2PDataStorage(Broadcaster broadcaster, NetworkNode networkNode, File storageDir) {
        this.broadcaster = broadcaster;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        storage = new Storage<>(storageDir);
        removeExpiredEntriesExecutor = Utilities.getScheduledThreadPoolExecutor("removeExpiredEntries", 1, 10, 5);

        HashMap<ByteArray, MapValue> persisted = storage.initAndGetPersisted("SequenceNumberMap");
        if (persisted != null)
            sequenceNumberMap = getPurgedSequenceNumberMap(persisted);
    }

    public void onBootstrapComplete() {
        removeExpiredEntriesExecutor.scheduleAtFixedRate(() -> UserThread.execute(() -> {
            log.trace("removeExpiredEntries");
            // The moment when an object becomes expired will not be synchronous in the network and we could 
            // get add messages after the object has expired. To avoid repeated additions of already expired 
            // object when we get it sent from new peers, we don’t remove the sequence number from the map. 
            // That way an ADD message for an already expired data will fail because the sequence number 
            // is equal and not larger. 
            Map<ByteArray, ProtectedData> temp = new HashMap<>(map);
            Set<ProtectedData> toRemoveSet = new HashSet<>();
            temp.entrySet().stream()
                    .filter(entry -> entry.getValue().isExpired())
                    .forEach(entry -> {
                        ByteArray hashOfPayload = entry.getKey();
                        ProtectedData protectedData = map.get(hashOfPayload);
                        toRemoveSet.add(protectedData);
                        log.error("remove protectedData:\n\t" + protectedData);
                        map.remove(hashOfPayload);
                    });

            toRemoveSet.stream().forEach(
                    protectedDataToRemove -> hashMapChangedListeners.stream().forEach(
                            listener -> listener.onRemoved(protectedDataToRemove)));

            if (sequenceNumberMap.size() > 1000)
                sequenceNumberMap = getPurgedSequenceNumberMap(sequenceNumberMap);

        }), CHECK_TTL_INTERVAL_MILLIS, CHECK_TTL_INTERVAL_MILLIS, TimeUnit.MILLISECONDS);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof DataBroadcastMessage) {
            Log.traceCall(StringUtils.abbreviate(message.toString(), 100) + "\n\tconnection=" + connection);
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (message instanceof AddDataMessage) {
                    add(((AddDataMessage) message).data, peersNodeAddress);
                } else if (message instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) message).data, peersNodeAddress);
                } else if (message instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) message).data, peersNodeAddress);
                } else if (message instanceof RefreshTTLMessage) {
                    refreshTTL((RefreshTTLMessage) message, peersNodeAddress);
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
        if (connection.getPeersNodeAddressOptional().isPresent() && !closeConnectionReason.isIntended) {
            map.values().stream()
                    .forEach(protectedData -> {
                        ExpirablePayload expirablePayload = protectedData.expirablePayload;
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

    public void shutDown() {
        Log.traceCall();
        MoreExecutors.shutdownAndAwaitTermination(removeExpiredEntriesExecutor, 500, TimeUnit.MILLISECONDS);
    }

    public boolean add(ProtectedData protectedData, @Nullable NodeAddress sender) {
        return add(protectedData, sender, false);
    }

    public boolean add(ProtectedData protectedData, @Nullable NodeAddress sender, boolean forceBroadcast) {
        Log.traceCall();

        ByteArray hashOfPayload = getHashAsByteArray(protectedData.expirablePayload);
        boolean result = checkPublicKeys(protectedData, true)
                && checkSignature(protectedData)
                && isSequenceNrValid(protectedData.sequenceNumber, hashOfPayload);

        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey)
            result &= checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedData.ownerPubKey, hashOfPayload);

        if (result) {
            map.put(hashOfPayload, protectedData);

            // Republished data have a larger sequence number. We set the rePublish flag to enable broadcasting 
            // even we had the data with the old seq nr. already
            if (sequenceNumberMap.containsKey(hashOfPayload) &&
                    protectedData.sequenceNumber > sequenceNumberMap.get(hashOfPayload).sequenceNr)

                sequenceNumberMap.put(hashOfPayload, new MapValue(protectedData.sequenceNumber, System.currentTimeMillis()));
            storage.queueUpForSave(sequenceNumberMap, 5000);

            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set after doAdd (truncated)");
            map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
            sb.append("\n------------------------------------------------------------\n");
            log.trace(sb.toString());
            log.info("Data set after doAdd: size=" + map.values().size());

            if (!containsKey || forceBroadcast)
                broadcast(new AddDataMessage(protectedData), sender);
            else
                log.trace("Not broadcasting data as we had it already in our map.");

            hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedData));
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public boolean refreshTTL(RefreshTTLMessage refreshTTLMessage, @Nullable NodeAddress sender) {
        Log.traceCall();

        byte[] hashOfDataAndSeqNr = refreshTTLMessage.hashOfDataAndSeqNr;
        byte[] signature = refreshTTLMessage.signature;
        ByteArray hashOfPayload = new ByteArray(refreshTTLMessage.hashOfPayload);
        int sequenceNumber = refreshTTLMessage.sequenceNumber;

        if (map.containsKey(hashOfPayload)) {
            ProtectedData storedData = map.get(hashOfPayload);

            if (storedData.expirablePayload instanceof StoragePayload) {
                if (sequenceNumberMap.containsKey(hashOfPayload) && sequenceNumberMap.get(hashOfPayload).sequenceNr == sequenceNumber) {
                    log.warn("We got that message with that seq nr already from another peer. We ignore that message.");
                    return true;
                } else {
                    PublicKey ownerPubKey = ((StoragePayload) storedData.expirablePayload).getOwnerPubKey();
                    boolean result = checkSignature(ownerPubKey, hashOfDataAndSeqNr, signature) &&
                            isSequenceNrValid(sequenceNumber, hashOfPayload) &&
                            checkIfStoredDataPubKeyMatchesNewDataPubKey(ownerPubKey, hashOfPayload);

                    if (result) {
                        log.error("refreshDate called for storedData:\n\t" + StringUtils.abbreviate(storedData.toString(), 100));
                        storedData.refreshDate();

                        sequenceNumberMap.put(hashOfPayload, new MapValue(sequenceNumber, System.currentTimeMillis()));
                        storage.queueUpForSave(sequenceNumberMap, 5000);

                        StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
                        sb.append("Data set after refreshTTL (truncated)");
                        map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
                        sb.append("\n------------------------------------------------------------\n");
                        log.trace(sb.toString());
                        log.info("Data set after addProtectedExpirableData: size=" + map.values().size());

                        broadcast(refreshTTLMessage, sender);
                    } else {
                        log.warn("Checks for refreshTTL failed");
                    }
                    return result;
                }
            } else {
                log.error("storedData.expirablePayload NOT instanceof StoragePayload. That must not happen.");
                return false;
            }
        } else {
            log.warn("We don't have data for that refresh message in our map.");
            return false;
        }
    }

    public boolean remove(ProtectedData protectedData, @Nullable NodeAddress sender) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(protectedData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedData, false)
                && isSequenceNrValid(protectedData.sequenceNumber, hashOfPayload)
                && checkSignature(protectedData)
                && checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedData.ownerPubKey, hashOfPayload);


        if (result) {
            doRemoveProtectedExpirableData(protectedData, hashOfPayload);

            broadcast(new RemoveDataMessage(protectedData), sender);

            sequenceNumberMap.put(hashOfPayload, new MapValue(protectedData.sequenceNumber, System.currentTimeMillis()));
            storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    public boolean removeMailboxData(ProtectedMailboxData protectedMailboxData, @Nullable NodeAddress sender) {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(protectedMailboxData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxData, false)
                && isSequenceNrValid(protectedMailboxData.sequenceNumber, hashOfData)
                && protectedMailboxData.receiversPubKey.equals(protectedMailboxData.ownerPubKey) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxData)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxData.receiversPubKey, hashOfData);

        if (result) {
            doRemoveProtectedExpirableData(protectedMailboxData, hashOfData);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxData), sender);

            sequenceNumberMap.put(hashOfData, new MapValue(protectedMailboxData.sequenceNumber, System.currentTimeMillis()));
            storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("removeMailboxData failed");
        }
        return result;
    }


    public Map<ByteArray, ProtectedData> getMap() {
        return map;
    }

    public ProtectedData getProtectedData(ExpirablePayload payload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfData = getHashAsByteArray(payload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(payload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedData(payload, payload.getTTL(), ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public RefreshTTLMessage getRefreshTTLMessage(ExpirablePayload payload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfPayload = getHashAsByteArray(payload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfPayload))
            sequenceNumber = sequenceNumberMap.get(hashOfPayload).sequenceNr + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(payload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new RefreshTTLMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
    }

    public ProtectedMailboxData getMailboxDataWithSignedSeqNr(MailboxPayload expirableMailboxPayload,
                                                              KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        ByteArray hashOfData = getHashAsByteArray(expirableMailboxPayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(expirableMailboxPayload, sequenceNumber));
        byte[] signature = Sig.sign(storageSignaturePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedMailboxData(expirableMailboxPayload, expirableMailboxPayload.getTTL(),
                storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey);
    }

    public void addHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        hashMapChangedListeners.add(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doRemoveProtectedExpirableData(ProtectedData protectedData, ByteArray hashOfPayload) {
        map.remove(hashOfPayload);
        log.trace("Data removed from our map. We broadcast the message to our peers.");
        hashMapChangedListeners.stream().forEach(e -> e.onRemoved(protectedData));

        StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Data set after removeProtectedExpirableData: (truncated)");
        map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
        sb.append("\n------------------------------------------------------------\n");
        log.trace(sb.toString());
        log.info("Data set after addProtectedExpirableData: size=" + map.values().size());
    }

    private boolean isSequenceNrValid(int newSequenceNumber, ByteArray hashOfData) {
        if (sequenceNumberMap.containsKey(hashOfData)) {
            Integer storedSequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr;
            if (newSequenceNumber < storedSequenceNumber) {
                log.warn("Sequence number is invalid. newSequenceNumber="
                        + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
                return false;
            } else {
                return true;
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

    private boolean checkSignature(ProtectedData data) {
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(data.expirablePayload, data.sequenceNumber));
        return checkSignature(data.ownerPubKey, hashOfDataAndSeqNr, data.signature);
    }

    private boolean checkPublicKeys(ProtectedData data, boolean isAddOperation) {
        boolean result = false;
        if (data.expirablePayload instanceof MailboxPayload) {
            MailboxPayload expirableMailboxPayload = (MailboxPayload) data.expirablePayload;
            if (isAddOperation)
                result = expirableMailboxPayload.senderPubKeyForAddOperation.equals(data.ownerPubKey);
            else
                result = expirableMailboxPayload.receiverPubKeyForRemoveOperation.equals(data.ownerPubKey);
        } else if (data.expirablePayload instanceof StoragePayload) {
            result = ((StoragePayload) data.expirablePayload).getOwnerPubKey().equals(data.ownerPubKey);
        }

        if (!result)
            log.error("PublicKey of payload data and ProtectedData are not matching. Consider it might be an attempt of fraud");
        return result;
    }

    private boolean checkIfStoredDataPubKeyMatchesNewDataPubKey(PublicKey ownerPubKey, ByteArray hashOfData) {
        if (map.containsKey(hashOfData)) {
            ProtectedData storedData = map.get(hashOfData);
            boolean result = storedData.ownerPubKey.equals(ownerPubKey);
            if (!result)
                log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

            return result;
        } else {
            return false;
        }
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(PublicKey receiversPubKey, ByteArray hashOfData) {
        ProtectedData storedData = map.get(hashOfData);
        if (storedData instanceof ProtectedMailboxData) {
            ProtectedMailboxData storedMailboxData = (ProtectedMailboxData) storedData;
            // publicKey is not the same (stored: sender, new: receiver)
            boolean result = storedMailboxData.receiversPubKey.equals(receiversPubKey)
                    && getHashAsByteArray(storedMailboxData.expirablePayload).equals(hashOfData);
            if (!result)
                log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

            return result;
        } else {
            log.error("We expected a MailboxData but got other type. That must never happen. storedData=" + storedData);
            return false;
        }
    }

    private void broadcast(DataBroadcastMessage message, @Nullable NodeAddress sender) {
        broadcaster.broadcast(message, sender);
    }

    private ByteArray getHashAsByteArray(ExpirablePayload data) {
        return new ByteArray(Hash.getHash(data));
    }

    private HashMap<ByteArray, MapValue> getPurgedSequenceNumberMap(HashMap<ByteArray, MapValue> persisted) {
        HashMap<ByteArray, MapValue> purged = new HashMap<>();
        long maxAgeTs = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10);
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
