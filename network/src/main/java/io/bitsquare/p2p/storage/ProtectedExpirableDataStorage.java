package io.bitsquare.p2p.storage;

import com.google.common.annotations.VisibleForTesting;
import io.bitsquare.common.UserThread;
import io.bitsquare.common.crypto.CryptoUtil;
import io.bitsquare.p2p.Address;
import io.bitsquare.p2p.network.IllegalRequest;
import io.bitsquare.p2p.network.MessageListener;
import io.bitsquare.p2p.routing.Routing;
import io.bitsquare.p2p.storage.data.*;
import io.bitsquare.p2p.storage.messages.*;
import io.bitsquare.storage.Storage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.math.BigInteger;
import java.security.*;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class ProtectedExpirableDataStorage {
    private static final Logger log = LoggerFactory.getLogger(ProtectedExpirableDataStorage.class);

    @VisibleForTesting
    public static int CHECK_TTL_INTERVAL = 10 * 60 * 1000;

    private final Routing routing;
    private final Map<BigInteger, ProtectedData> map = new ConcurrentHashMap<>();
    private final List<HashSetChangedListener> hashSetChangedListeners = new CopyOnWriteArrayList<>();
    private ConcurrentHashMap<BigInteger, Integer> sequenceNumberMap = new ConcurrentHashMap<>();
    private final Storage<ConcurrentHashMap> storage;
    private boolean authenticated;
    private final Timer timer = new Timer();
    private volatile boolean shutDownInProgress;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public ProtectedExpirableDataStorage(Routing routing, File storageDir) {
        this.routing = routing;

        storage = new Storage<>(storageDir);

        ConcurrentHashMap<BigInteger, Integer> persisted = storage.initAndGetPersisted(sequenceNumberMap, "sequenceNumberMap");
        if (persisted != null) {
            sequenceNumberMap = persisted;
        }

        addMessageListener((message, connection) -> {
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
                    connection.reportIllegalRequest(IllegalRequest.NotAuthenticated);
                }
            }
        });

        timer.scheduleAtFixedRate(new TimerTask() {
                                      @Override
                                      public void run() {
                                          log.info("removeExpiredEntries called ");
                                          map.entrySet().stream().filter(entry -> entry.getValue().isExpired())
                                                  .forEach(entry -> map.remove(entry.getKey()));
                                      }
                                  },
                CHECK_TTL_INTERVAL,
                CHECK_TTL_INTERVAL);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (!shutDownInProgress) {
            shutDownInProgress = true;
            timer.cancel();
            routing.shutDown();
        }
    }

    public void setAuthenticated(boolean authenticated) {
        this.authenticated = authenticated;
    }

    public boolean add(ProtectedData protectedData, Address sender) {
        BigInteger hashOfPayload = getHashAsBigInteger(protectedData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfPayload);
        boolean result = checkPublicKeys(protectedData, true)
                && isSequenceNrValid(protectedData, hashOfPayload)
                && checkSignature(protectedData)
                && (!containsKey || checkIfStoredDataMatchesNewData(protectedData, hashOfPayload))
                && doAddProtectedExpirableData(protectedData, hashOfPayload, sender);

        if (result) {
            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave();
        } else {
            log.debug("add failed");
        }
        return result;
    }

    public boolean remove(ProtectedData protectedData, Address sender) {
        BigInteger hashOfPayload = getHashAsBigInteger(protectedData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfPayload);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedData, false)
                && isSequenceNrValid(protectedData, hashOfPayload)
                && checkSignature(protectedData)
                && checkIfStoredDataMatchesNewData(protectedData, hashOfPayload)
                && doRemoveProtectedExpirableData(protectedData, hashOfPayload, sender);

        if (result) {
            sequenceNumberMap.put(hashOfPayload, protectedData.sequenceNumber);
            storage.queueUpForSave();
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    public boolean removeMailboxData(ProtectedMailboxData protectedMailboxData, Address sender) {
        BigInteger hashOfData = getHashAsBigInteger(protectedMailboxData.expirablePayload);
        boolean containsKey = map.containsKey(hashOfData);
        if (!containsKey) log.debug("Remove data ignored as we don't have an entry for that data.");
        boolean result = containsKey
                && checkPublicKeys(protectedMailboxData, false)
                && isSequenceNrValid(protectedMailboxData, hashOfData)
                && protectedMailboxData.receiversPubKey.equals(protectedMailboxData.ownerStoragePubKey) // at remove both keys are the same (only receiver is able to remove data)
                && checkSignature(protectedMailboxData)
                && checkIfStoredMailboxDataMatchesNewMailboxData(protectedMailboxData, hashOfData)
                && doRemoveProtectedExpirableData(protectedMailboxData, hashOfData, sender);

        if (result) {
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

    public ProtectedData getDataWithSignedSeqNr(ExpirablePayload payload, KeyPair ownerStoragePubKey) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        BigInteger hashOfData = getHashAsBigInteger(payload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = CryptoUtil.getHash(new DataAndSeqNr(payload, sequenceNumber));
        byte[] signature = CryptoUtil.signStorageData(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedData(payload, payload.getTTL(), ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public ProtectedMailboxData getMailboxDataWithSignedSeqNr(ExpirableMailboxPayload expirableMailboxPayload, KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        BigInteger hashOfData = getHashAsBigInteger(expirableMailboxPayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData) + 1;
        else
            sequenceNumber = 0;

        byte[] hashOfDataAndSeqNr = CryptoUtil.getHash(new DataAndSeqNr(expirableMailboxPayload, sequenceNumber));
        byte[] signature = CryptoUtil.signStorageData(storageSignaturePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedMailboxData(expirableMailboxPayload, expirableMailboxPayload.getTTL(), storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey);
    }

    public void addHashSetChangedListener(HashSetChangedListener hashSetChangedListener) {
        hashSetChangedListeners.add(hashSetChangedListener);
    }

    public void addMessageListener(MessageListener messageListener) {
        routing.addMessageListener(messageListener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private boolean isSequenceNrValid(ProtectedData data, BigInteger hashOfData) {
        int newSequenceNumber = data.sequenceNumber;
        Integer storedSequenceNumber = sequenceNumberMap.get(hashOfData);
        if (sequenceNumberMap.containsKey(hashOfData) && newSequenceNumber <= storedSequenceNumber) {
            log.warn("Sequence number is invalid. That might happen in rare cases. newSequenceNumber="
                    + newSequenceNumber + " / storedSequenceNumber=" + storedSequenceNumber);
            return false;
        } else {
            return true;
        }
    }

    private boolean checkSignature(ProtectedData data) {
        byte[] hashOfDataAndSeqNr = CryptoUtil.getHash(new DataAndSeqNr(data.expirablePayload, data.sequenceNumber));
        try {
            boolean result = CryptoUtil.verifyStorageData(data.ownerStoragePubKey, hashOfDataAndSeqNr, data.signature);
            if (!result)
                log.error("Signature verification failed at checkSignature. " +
                        "That should not happen. Consider it might be an attempt of fraud.");

            return result;
        } catch (NoSuchAlgorithmException | InvalidKeyException | SignatureException e) {
            log.error("Signature verification failed at checkSignature");
            return false;
        }
    }

    private boolean checkPublicKeys(ProtectedData data, boolean isAddOperation) {
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
        ProtectedData storedData = map.get(hashOfData);
        boolean result = getHashAsBigInteger(storedData.expirablePayload).equals(hashOfData)
                && storedData.ownerStoragePubKey.equals(data.ownerStoragePubKey);
        if (!result)
            log.error("New data entry does not match our stored data. Consider it might be an attempt of fraud");

        return result;
    }

    private boolean checkIfStoredMailboxDataMatchesNewMailboxData(ProtectedMailboxData data, BigInteger hashOfData) {
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

    private boolean doAddProtectedExpirableData(ProtectedData data, BigInteger hashOfData, Address sender) {
        map.put(hashOfData, data);
        log.trace("Data added to our map and it will be broadcasted to our neighbors.");
        UserThread.execute(() -> hashSetChangedListeners.stream().forEach(e -> e.onAdded(data)));
        broadcast(new AddDataMessage(data), sender);

        StringBuilder sb = new StringBuilder("\n\nSet after addProtectedExpirableData:\n");
        map.values().stream().forEach(e -> sb.append(e.toString() + "\n\n"));
        sb.append("\n\n");
        log.trace(sb.toString());
        return true;
    }

    private boolean doRemoveProtectedExpirableData(ProtectedData data, BigInteger hashOfData, Address sender) {
        map.remove(hashOfData);
        log.trace("Data removed from our map. We broadcast the message to our neighbors.");
        UserThread.execute(() -> hashSetChangedListeners.stream().forEach(e -> e.onRemoved(data)));
        if (data instanceof ProtectedMailboxData)
            broadcast(new RemoveMailboxDataMessage((ProtectedMailboxData) data), sender);
        else
            broadcast(new RemoveDataMessage(data), sender);

        StringBuilder sb = new StringBuilder("\n\nSet after removeProtectedExpirableData:\n");
        map.values().stream().forEach(e -> sb.append(e.toString() + "\n\n"));
        sb.append("\n\n");
        log.trace(sb.toString());
        return true;
    }

    private void broadcast(BroadcastMessage message, Address sender) {
        if (authenticated) {
            routing.broadcast(message, sender);
            log.trace("Broadcast message " + message);
        } else {
            log.trace("Broadcast not allowed because we are not authenticated yet. That is normal after received AllDataMessage at startup.");
        }
    }

    private BigInteger getHashAsBigInteger(ExpirablePayload payload) {
        return new BigInteger(CryptoUtil.getHash(payload));
    }
}
