package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.util.concurrent.MoreExecutors;
import io.bitsquare.app.Log;
import io.bitsquare.common.ByteArray;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.*;
import io.bitsquare.p2p.peers.Broadcaster;
import io.bitsquare.p2p.storage.data.*;
import io.bitsquare.p2p.storage.messages.AddDataMessage;
import io.bitsquare.p2p.storage.messages.DataBroadcastMessage;
import io.bitsquare.p2p.storage.messages.RemoveDataMessage;
import io.bitsquare.p2p.storage.messages.RemoveMailboxDataMessage;
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
    public static int CHECK_TTL_INTERVAL_SEC = new Random().nextInt(60) + (int) TimeUnit.MINUTES.toSeconds(10); // 10-11 min.
    //TODO
    // public static int CHECK_TTL_INTERVAL_SEC = 10; 

    private final Broadcaster broadcaster;
    private final Map<ByteArray, ProtectedData> map = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private HashMap<ByteArray, Integer> sequenceNumberMap = new HashMap<>();
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

        log.debug("CHECK_TTL_INTERVAL_SEC " + CHECK_TTL_INTERVAL_SEC);
        init();
    }

    private void init() {
        HashMap<ByteArray, Integer> persisted = storage.initAndGetPersisted("SequenceNumberMap");
        if (persisted != null)
            sequenceNumberMap = persisted;

        removeExpiredEntriesExecutor.scheduleAtFixedRate(() -> UserThread.execute(this::removeExpiredEntries), CHECK_TTL_INTERVAL_SEC, CHECK_TTL_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private void removeExpiredEntries() {
        Log.traceCall();
        // The moment when an object becomes expired will not be synchronous in the network and we could 
        // get add messages after the object has expired. To avoid repeated additions of already expired 
        // object when we get it sent from new peers, we don’t remove the sequence number from the map. 
        // That way an ADD message for an already expired data will fail because the sequence number 
        // is equal and not larger. 
        Map<ByteArray, ProtectedData> temp = new HashMap<>(map);
        Set<ProtectedData> protectedDataToRemoveSet = new HashSet<>();
        temp.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .forEach(entry -> {
                    ByteArray hashOfPayload = entry.getKey();
                    ProtectedData protectedDataToRemove = map.get(hashOfPayload);
                    protectedDataToRemoveSet.add(protectedDataToRemove);
                    map.remove(hashOfPayload);
                });

        protectedDataToRemoveSet.stream().forEach(
                protectedDataToRemove -> hashMapChangedListeners.stream().forEach(
                        listener -> listener.onRemoved(protectedDataToRemove)));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        if (message instanceof DataBroadcastMessage) {
            Log.traceCall(message.toString() + "\n\tconnection=" + connection);
            log.trace("DataBroadcastMessage received " + message + "\n\tconnection " + connection);
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (message instanceof AddDataMessage) {
                    add(((AddDataMessage) message).data, peersNodeAddress);
                } else if (message instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) message).data, peersNodeAddress);
                } else if (message instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) message).data, peersNodeAddress);
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
        Log.traceCall();
        map.values().stream()
                .filter(protectedData -> protectedData.expirableMessage instanceof RequiresLiveOwner)
                .forEach(protectedData -> removeRequiresLiveOwnerDataOnDisconnect(protectedData, ((RequiresLiveOwner) protectedData.expirableMessage).getOwnerNodeAddress()));
    }

    public boolean removeRequiresLiveOwnerDataOnDisconnect(ProtectedData protectedData, NodeAddress owner) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(protectedData.expirableMessage);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey) {
            doRemoveProtectedExpirableData(protectedData, hashOfPayload);

            //broadcast(new RemoveDataMessage(protectedData), owner);

            // sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            sequenceNumberMap.remove(hashOfPayload);
            storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("Remove data ignored as we don't have an entry for that data.");
        }
        return containsKey;
    }

    // If the data owner gets disconnected we remove his data. Used for offers to get clean up when the peer is in 
    // sleep/hibernate mode or closes the app without proper shutdown (crash).
    // We don't want to wait the until the TTL period is over so we add that method to improve usability
    public boolean removeLocalDataOnDisconnectedDataOwner(ExpirableMessage expirableMessage) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(expirableMessage);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey) {
            map.remove(hashOfPayload);
            log.trace("Data removed from our map.");

            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n" +
                    "Data set after removeProtectedExpirableData: (truncated)");
            map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
            sb.append("\n------------------------------------------------------------\n");
            log.trace(sb.toString());
            log.info("Data set after addProtectedExpirableData: size=" + map.values().size());

            // sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            // storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("Remove data ignored as we don't have an entry for that data.");
        }
        return containsKey;
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
        Log.traceCall();
        return doAdd(protectedData, sender, false);
    }

    public boolean rePublish(ProtectedData protectedData, @Nullable NodeAddress sender) {
        Log.traceCall();
        return doAdd(protectedData, sender, true);
    }

    private boolean doAdd(ProtectedData protectedData, @Nullable NodeAddress sender, boolean rePublish) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(protectedData.expirableMessage);
        boolean result = checkPublicKeys(protectedData, true)
                && checkSignature(protectedData)
                && isSequenceNrValid(protectedData, hashOfPayload);

        boolean containsKey = map.containsKey(hashOfPayload);
        if (containsKey)
            result &= checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedData, hashOfPayload);

        if (result) {
            map.put(hashOfPayload, protectedData);

            // Republished data have a larger sequence number. We set the rePublish flag to enable broadcasting 
            // even we had the data with the old seq nr. already
            if (sequenceNumberMap.containsKey(hashOfPayload) &&
                    protectedData.sequenceNumber > sequenceNumberMap.get(hashOfPayload))
                rePublish = true;

            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave(sequenceNumberMap, 5000);

            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set after addProtectedExpirableData (truncated)");
            map.values().stream().forEach(e -> sb.append("\n").append(StringUtils.abbreviate(e.toString(), 100)));
            sb.append("\n------------------------------------------------------------\n");
            log.trace(sb.toString());
            log.info("Data set after addProtectedExpirableData: size=" + map.values().size());

            if (rePublish || !containsKey)
                broadcast(new AddDataMessage(protectedData), sender);

            hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedData));
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public boolean remove(ProtectedData protectedData, @Nullable NodeAddress sender) {
        Log.traceCall();
        ByteArray hashOfPayload = getHashAsByteArray(protectedData.expirableMessage);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedData, false)
                && isSequenceNrValid(protectedData, hashOfPayload)
                && checkSignature(protectedData)
                && checkIfStoredDataPubKeyMatchesNewDataPubKey(protectedData, hashOfPayload);


        if (result) {
            doRemoveProtectedExpirableData(protectedData, hashOfPayload);

            broadcast(new RemoveDataMessage(protectedData), sender);

            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    public boolean removeMailboxData(ProtectedMailboxData protectedMailboxData, @Nullable NodeAddress sender) {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(protectedMailboxData.expirableMessage);
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxData, false)
                && isSequenceNrValid(protectedMailboxData, hashOfData)
                && protectedMailboxData.receiversPubKey.equals(protectedMailboxData.ownerPubKey) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxData)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxData, hashOfData);

        if (result) {
            doRemoveProtectedExpirableData(protectedMailboxData, hashOfData);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxData), sender);

            sequenceNumberMap.put(hashOfData, protectedMailboxData.sequenceNumber);
            storage.queueUpForSave(sequenceNumberMap, 5000);
        } else {
            log.debug("removeMailboxData failed");
        }
        return result;
    }


    public Map<ByteArray, ProtectedData> getMap() {
        return map;
    }

    public ProtectedData getDataWithSignedSeqNr(ExpirableMessage payload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(payload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(payload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedData(payload, payload.getTTL(), ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public ProtectedMailboxData getMailboxDataWithSignedSeqNr(MailboxMessage expirableMailboxPayload,
                                                              KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        Log.traceCall();
        ByteArray hashOfData = getHashAsByteArray(expirableMailboxPayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
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
        Log.traceCall();
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

    private boolean isSequenceNrValid(ProtectedData data, ByteArray hashOfData) {
        Log.traceCall();
        int newSequenceNumber = data.sequenceNumber;
        Integer storedSequenceNumber = sequenceNumberMap.get(hashOfData);
        if (sequenceNumberMap.containsKey(hashOfData) && newSequenceNumber < storedSequenceNumber) {
            log.trace("Sequence number is invalid. newSequenceNumber="
                    + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
            return false;
        } else {
            return true;
        }
    }

    private boolean checkSignature(ProtectedData data) {
        Log.traceCall();
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNrPair(data.expirableMessage, data.sequenceNumber));
        try {
            boolean result = Sig.verify(data.ownerPubKey, hashOfDataAndSeqNr, data.signature);
            if (!result)
                log.error("Signature verification failed at checkSignature. " +
                        "That should not happen. Consider it might be an attempt of fraud.");

            return result;
        } catch (CryptoException e) {
            log.error("Signature verification failed at checkSignature");
            return false;
        }
    }

    private boolean checkPublicKeys(ProtectedData data, boolean isAddOperation) {
        Log.traceCall();
        boolean result = false;
        if (data.expirableMessage instanceof MailboxMessage) {
            MailboxMessage expirableMailboxPayload = (MailboxMessage) data.expirableMessage;
            if (isAddOperation)
                result = expirableMailboxPayload.senderPubKeyForAddOperation.equals(data.ownerPubKey);
            else
                result = expirableMailboxPayload.receiverPubKeyForRemoveOperation.equals(data.ownerPubKey);
        } else if (data.expirableMessage instanceof StorageMessage) {
            result = ((StorageMessage) data.expirableMessage).getOwnerPubKey().equals(data.ownerPubKey);
        }

        if (!result)
            log.error("PublicKey of payload data and ProtectedData are not matching. Consider it might be an attempt of fraud");
        return result;
    }

    private boolean checkIfStoredDataPubKeyMatchesNewDataPubKey(ProtectedData data, ByteArray hashOfData) {
        Log.traceCall();
        ProtectedData storedData = map.get(hashOfData);
        boolean result = storedData.ownerPubKey.equals(data.ownerPubKey);
        if (!result)
            log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

        return result;
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(ProtectedMailboxData data, ByteArray hashOfData) {
        Log.traceCall();
        ProtectedData storedData = map.get(hashOfData);
        if (storedData instanceof ProtectedMailboxData) {
            ProtectedMailboxData storedMailboxData = (ProtectedMailboxData) storedData;
            // publicKey is not the same (stored: sender, new: receiver)
            boolean result = storedMailboxData.receiversPubKey.equals(data.receiversPubKey)
                    && getHashAsByteArray(storedMailboxData.expirableMessage).equals(hashOfData);
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

    private ByteArray getHashAsByteArray(ExpirableMessage payload) {
        return new ByteArray(Hash.getHash(payload));
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static class
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static class DataAndSeqNrPair implements Serializable {
        // data are only used for getting cryptographic hash from both values
        private final Serializable data;
        private final int sequenceNumber;

        public DataAndSeqNrPair(Serializable data, int sequenceNumber) {
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

}
