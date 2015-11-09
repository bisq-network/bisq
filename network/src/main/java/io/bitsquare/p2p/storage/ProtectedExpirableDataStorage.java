package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.app.Log;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoException;
import io.bitsquare.common.crypto.Hash;
import io.bitsquare.common.crypto.Sig;
import io.bitsquare.common.util.Utilities;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.Message;
import io.bitsquare.p2p.network.Connection;
import io.bitsquare.p2p.network.IllegalRequest;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.network.NetworkNode;
import io.bitsquare.p2p.peers.PeerGroup;
import io.bitsquare.p2p.storage.data.*;
import io.bitsquare.p2p.storage.messages.*;
import io.bitsquare.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.io.File;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

// Run in UserThread
public class ProtectedExpirableDataStorage implements MessageListener {
    private static final Logger log = LoggerFactory.getLogger(ProtectedExpirableDataStorage.class);

    @VisibleForTesting
    public static int CHECK_TTL_INTERVAL = 10 * 60 * 1000;

    private final PeerGroup peerGroup;
    private final Map<BigInteger, ProtectedData> map = new ConcurrentHashMap<>();
    private final CopyOnWriteArraySet<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private ConcurrentHashMap<BigInteger, Integer> sequenceNumberMap = new ConcurrentHashMap<>();
    private final Storage<ConcurrentHashMap> storage;
    private final Timer timer = new Timer();
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProtectedExpirableDataStorage(PeerGroup peerGroup, File storageDir) {
        Log.traceCall();
        this.peerGroup = peerGroup;

        storage = new Storage<>(storageDir);

        init();
    }

    private void init() {
        Log.traceCall();
        ConcurrentHashMap<BigInteger, Integer> persisted = storage.initAndGetPersisted(sequenceNumberMap, "sequenceNumberMap");
        if (persisted != null) {
            sequenceNumberMap = persisted;
        }

        NetworkNode networkNode = peerGroup.getNetworkNode();
        networkNode.addMessageListener(this);

        timer.scheduleAtFixedRate(new TimerTask() {
                                      @Override
                                      public void run() {
                                          try {
                                              Utilities.setThreadName("RemoveExpiredEntriesTimer");
                                              UserThread.execute(() -> removeExpiredEntries());
                                          } catch (Throwable t) {
                                              log.error("Executing task failed. " + t.getMessage());
                                              t.printStackTrace();
                                          }
                                      }
                                  },
                CHECK_TTL_INTERVAL, CHECK_TTL_INTERVAL);
    }

    private void removeExpiredEntries() {
        Log.traceCall();
        map.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired())
                .forEach(entry -> map.remove(entry.getKey()));
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(Message message, Connection connection) {
        Log.traceCall("Message=" + message);
        if (message instanceof DataMessage) {
            if (connection.isAuthenticated()) {
                log.trace("ProtectedExpirableDataMessage received " + message + " on connection " + connection);
                if (message instanceof AddDataMessage) {
                    add(((AddDataMessage) message).data, connection.getPeerAddress());
                } else if (message instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) message).data, connection.getPeerAddress());
                } else if (message instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) message).data, connection.getPeerAddress());
                }
            } else {
                log.warn("Connection is not authenticated yet. We don't accept storage operations form non-authenticated nodes.");
                log.warn("Connection = " + connection);
                connection.reportIllegalRequest(IllegalRequest.NotAuthenticated);
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        Log.traceCall();
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            timer.cancel();
            peerGroup.shutDown();
        }
    }

    public boolean add(ProtectedData protectedData, @Nullable Address sender) {
        Log.traceCall();
        BigInteger hashOfPayload = getHashAsBigInteger(protectedData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfPayload);
        boolean result = checkPublicKeys(protectedData, true)
                && checkSignature(protectedData);

        if (containsKey) {
            result &= checkIfStoredDataMatchesNewData(protectedData, hashOfPayload)
                    && isSequenceNrValid(protectedData, hashOfPayload);
        }

        if (result) {
            map.put(hashOfPayload, protectedData);
            log.trace("Data added to our map and it will be broadcasted to our peers.");
            hashMapChangedListeners.stream().forEach(e -> e.onAdded(protectedData));

            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set after addProtectedExpirableData:");
            map.values().stream().forEach(e -> sb.append("\n").append(e.toString()).append("\n"));
            sb.append("\n------------------------------------------------------------\n");
            log.info(sb.toString());

            if (!containsKey)
                broadcast(new AddDataMessage(protectedData), sender);

            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave();
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public boolean remove(ProtectedData protectedData, @Nullable Address sender) {
        Log.traceCall();
        BigInteger hashOfPayload = getHashAsBigInteger(protectedData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedData, false)
                && isSequenceNrValid(protectedData, hashOfPayload)
                && checkSignature(protectedData)
                && checkIfStoredDataMatchesNewData(protectedData, hashOfPayload);


        if (result) {
            doRemoveProtectedExpirableData(protectedData, hashOfPayload);

            broadcast(new RemoveDataMessage(protectedData), sender);

            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave();
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    public boolean removeMailboxData(ProtectedMailboxData protectedMailboxData, @Nullable Address sender) {
        Log.traceCall();
        BigInteger hashOfData = getHashAsBigInteger(protectedMailboxData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxData, false)
                && isSequenceNrValid(protectedMailboxData, hashOfData)
                && protectedMailboxData.receiversPubKey.equals(protectedMailboxData.ownerStoragePubKey) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxData)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxData, hashOfData);

        if (result) {
            doRemoveProtectedExpirableData(protectedMailboxData, hashOfData);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxData), sender);

            sequenceNumberMap.put(hashOfData, protectedMailboxData.sequenceNumber);
            storage.queueUpForSave();
        } else {
            log.debug("removeMailboxData failed");
        }
        return result;
    }

    public Map<BigInteger, ProtectedData> getMap() {
        return map;
    }

    public ProtectedData getDataWithSignedSeqNr(ExpirablePayload payload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        Log.traceCall();
        BigInteger hashOfData = getHashAsBigInteger(payload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(payload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedData(payload, payload.getTTL(), ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public ProtectedMailboxData getMailboxDataWithSignedSeqNr(ExpirableMailboxPayload expirableMailboxPayload,
                                                              KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        Log.traceCall();
        BigInteger hashOfData = getHashAsBigInteger(expirableMailboxPayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(expirableMailboxPayload, sequenceNumber));
        byte[] signature = Sig.sign(storageSignaturePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedMailboxData(expirableMailboxPayload, expirableMailboxPayload.getTTL(),
                storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey);
    }

    public void addHashMapChangedListener(HashMapChangedListener hashMapChangedListener) {
        Log.traceCall();
        hashMapChangedListeners.add(hashMapChangedListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void doRemoveProtectedExpirableData(ProtectedData protectedData, BigInteger hashOfPayload) {
        Log.traceCall();
        map.remove(hashOfPayload);
        log.trace("Data removed from our map. We broadcast the message to our peers.");
        hashMapChangedListeners.stream().forEach(e -> e.onRemoved(protectedData));

        StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n" +
                "Data set after removeProtectedExpirableData:");
        map.values().stream().forEach(e -> sb.append("\n").append(e.toString()));
        sb.append("\n------------------------------------------------------------\n");
        log.info(sb.toString());
    }

    private boolean isSequenceNrValid(ProtectedData data, BigInteger hashOfData) {
        Log.traceCall();
        int newSequenceNumber = data.sequenceNumber;
        Integer storedSequenceNumber = sequenceNumberMap.get(hashOfData);
        if (sequenceNumberMap.containsKey(hashOfData) && newSequenceNumber <= storedSequenceNumber) {
            log.trace("Sequence number is invalid. newSequenceNumber="
                    + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
            return false;
        } else {
            return true;
        }
    }

    private boolean checkSignature(ProtectedData data) {
        Log.traceCall();
        byte[] hashOfDataAndSeqNr = Hash.getHash(new DataAndSeqNr(data.expirablePayload, data.sequenceNumber));
        try {
            boolean result = Sig.verify(data.ownerStoragePubKey, hashOfDataAndSeqNr, data.signature);
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
        if (data.expirablePayload instanceof ExpirableMailboxPayload) {
            ExpirableMailboxPayload expirableMailboxPayload = (ExpirableMailboxPayload) data.expirablePayload;
            if (isAddOperation)
                result = expirableMailboxPayload.senderStoragePublicKey.equals(data.ownerStoragePubKey);
            else
                result = expirableMailboxPayload.receiverStoragePublicKey.equals(data.ownerStoragePubKey);
        } else if (data.expirablePayload instanceof PubKeyProtectedExpirablePayload) {
            result = ((PubKeyProtectedExpirablePayload) data.expirablePayload).getPubKey().equals(data.ownerStoragePubKey);
        }

        if (!result)
            log.error("PublicKey of payload data and ProtectedData are not matching. Consider it might be an attempt of fraud");
        return result;
    }

    private boolean checkIfStoredDataMatchesNewData(ProtectedData data, BigInteger hashOfData) {
        Log.traceCall();
        ProtectedData storedData = map.get(hashOfData);
        boolean result = getHashAsBigInteger(storedData.expirablePayload).equals(hashOfData)
                && storedData.ownerStoragePubKey.equals(data.ownerStoragePubKey);
        if (!result)
            log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

        return result;
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(ProtectedMailboxData data, BigInteger hashOfData) {
        Log.traceCall();
        ProtectedData storedData = map.get(hashOfData);
        if (storedData instanceof ProtectedMailboxData) {
            ProtectedMailboxData storedMailboxData = (ProtectedMailboxData) storedData;
            // publicKey is not the same (stored: sender, new: receiver)
            boolean result = storedMailboxData.receiversPubKey.equals(data.receiversPubKey)
                    && getHashAsBigInteger(storedMailboxData.expirablePayload).equals(hashOfData);
            if (!result)
                log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

            return result;
        } else {
            log.error("We expected a MailboxData but got other type. That must never happen. storedData=" + storedData);
            return false;
        }
    }

    private void broadcast(BroadcastMessage message, @Nullable Address sender) {
        Log.traceCall(message.toString());
        peerGroup.broadcast(message, sender);
    }

    private BigInteger getHashAsBigInteger(ExpirablePayload payload) {
        return new BigInteger(Hash.getHash(payload));
    }

}
