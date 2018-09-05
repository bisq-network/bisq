/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.network.p2p.storage;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.network.CloseConnectionReason;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.ConnectionListener;
import bisq.network.p2p.network.MessageListener;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreListener;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Log;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import io.bisq.generated.protobuffer.PB;

import com.google.protobuf.ByteString;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;

import org.apache.commons.lang3.StringUtils;

import org.bouncycastle.util.encoders.Hex;

import java.security.KeyPair;
import java.security.PublicKey;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.slf4j.LoggerFactory;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class P2PDataStorage implements MessageListener, ConnectionListener, PersistedDataHost {
    /**
     * How many days to keep an entry before it is purged.
     */
    private static final int PURGE_AGE_DAYS = 10;

    @VisibleForTesting
    public static int CHECK_TTL_INTERVAL_SEC = 60;

    private final Broadcaster broadcaster;
    private final AppendOnlyDataStoreService appendOnlyDataStoreService;
    private final ProtectedDataStoreService protectedDataStoreService;

    @Getter
    private final Map<ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();
    private final Set<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private Timer removeExpiredEntriesTimer;

    private final Storage<SequenceNumberMap> sequenceNumberMapStorage;
    private final SequenceNumberMap sequenceNumberMap = new SequenceNumberMap();

    private final Set<AppendOnlyDataStoreListener> appendOnlyDataStoreListeners = new CopyOnWriteArraySet<>();
    private final Set<ProtectedDataStoreListener> protectedDataStoreListeners = new CopyOnWriteArraySet<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public P2PDataStorage(NetworkNode networkNode,
                          Broadcaster broadcaster,
                          AppendOnlyDataStoreService appendOnlyDataStoreService,
                          ProtectedDataStoreService protectedDataStoreService,
                          Storage<SequenceNumberMap> sequenceNumberMapStorage) {
        this.broadcaster = broadcaster;
        this.appendOnlyDataStoreService = appendOnlyDataStoreService;
        this.protectedDataStoreService = protectedDataStoreService;

        networkNode.addMessageListener(this);
        networkNode.addConnectionListener(this);

        this.sequenceNumberMapStorage = sequenceNumberMapStorage;
        sequenceNumberMapStorage.setNumMaxBackupFiles(5);
    }

    @Override
    public void readPersisted() {
        SequenceNumberMap persistedSequenceNumberMap = sequenceNumberMapStorage.initAndGetPersisted(sequenceNumberMap, 300);
        if (persistedSequenceNumberMap != null)
            sequenceNumberMap.setMap(getPurgedSequenceNumberMap(persistedSequenceNumberMap.getMap()));
    }

    // This method is called at startup in a non-user thread.
    // We should not have any threading issues here as the p2p network is just initializing

    public synchronized void readFromResources(String postFix) {
        appendOnlyDataStoreService.readFromResources(postFix);
        protectedDataStoreService.readFromResources(postFix);

        map.putAll(protectedDataStoreService.getMap());
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
                        if (!(protectedStorageEntry.getProtectedStoragePayload() instanceof PersistableNetworkPayload)) {
                            toRemoveSet.add(protectedStorageEntry);
                            log.debug("We found an expired data entry. We remove the protectedData:\n\t" + Utilities.toTruncatedString(protectedStorageEntry));
                            map.remove(hashOfPayload);
                        }
                    });

            toRemoveSet.forEach(
                    protectedDataToRemove -> hashMapChangedListeners.forEach(
                            listener -> listener.onRemoved(protectedDataToRemove)));

            if (sequenceNumberMap.size() > 1000)
                sequenceNumberMap.setMap(getPurgedSequenceNumberMap(sequenceNumberMap.getMap()));
        }, CHECK_TTL_INTERVAL_SEC);
    }

    public Map<ByteArray, PersistableNetworkPayload> getAppendOnlyDataStoreMap() {
        return appendOnlyDataStoreService.getMap();
    }

    public Map<P2PDataStorage.ByteArray, ProtectedStorageEntry> getProtectedDataStoreMap() {
        return protectedDataStoreService.getMap();
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof BroadcastMessage) {
            Log.traceCall(Utilities.toTruncatedString(networkEnvelope) + "\n\tconnection=" + connection);
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (networkEnvelope instanceof AddDataMessage) {
                    addProtectedStorageEntry(((AddDataMessage) networkEnvelope).getProtectedStorageEntry(), peersNodeAddress, null, false);
                } else if (networkEnvelope instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) networkEnvelope).getProtectedStorageEntry(), peersNodeAddress, false);
                } else if (networkEnvelope instanceof RemoveMailboxDataMessage) {
                    removeMailboxData(((RemoveMailboxDataMessage) networkEnvelope).getProtectedMailboxStorageEntry(), peersNodeAddress, false);
                } else if (networkEnvelope instanceof RefreshOfferMessage) {
                    refreshTTL((RefreshOfferMessage) networkEnvelope, peersNodeAddress, false);
                } else if (networkEnvelope instanceof AddPersistableNetworkPayloadMessage) {
                    addPersistableNetworkPayload(((AddPersistableNetworkPayloadMessage) networkEnvelope).getPersistableNetworkPayload(),
                            peersNodeAddress, false, true, false, true);
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
            map.values()
                    .forEach(protectedStorageEntry -> {
                        NetworkPayload networkPayload = protectedStorageEntry.getProtectedStoragePayload();
                        if (networkPayload instanceof ExpirablePayload && networkPayload instanceof RequiresOwnerIsOnlinePayload) {
                            NodeAddress ownerNodeAddress = ((RequiresOwnerIsOnlinePayload) networkPayload).getOwnerNodeAddress();
                            if (ownerNodeAddress.equals(connection.getPeersNodeAddressOptional().get())) {
                                // We have a RequiresLiveOwnerData data object with the node address of the
                                // disconnected peer. We remove that data from our map.

                                // Check if we have the data (e.g. OfferPayload)
                                ByteArray hashOfPayload = get32ByteHashAsByteArray(networkPayload);
                                boolean containsKey = map.containsKey(hashOfPayload);
                                if (containsKey) {
                                    log.debug("We remove the data as the data owner got disconnected with " +
                                            "closeConnectionReason=" + closeConnectionReason);

                                    // We only set the data back by half of the TTL and remove the data only if is has
                                    // expired after that back dating.
                                    // We might get connection drops which are not caused by the node going offline, so
                                    // we give more tolerance with that approach, giving the node the change to
                                    // refresh the TTL with a refresh message.
                                    // We observed those issues during stress tests, but it might have been caused by the
                                    // test set up (many nodes/connections over 1 router)
                                    // TODO investigate what causes the disconnections.
                                    // Usually the are: SOCKET_TIMEOUT ,TERMINATED (EOFException)
                                    protectedStorageEntry.backDate();
                                    if (protectedStorageEntry.isExpired()) {
                                        log.info("We found an expired data entry which we have already back dated. " +
                                                "We remove the protectedStoragePayload:\n\t" + Utilities.toTruncatedString(protectedStorageEntry.getProtectedStoragePayload(), 100));
                                        doRemoveProtectedExpirableData(protectedStorageEntry, hashOfPayload);
                                    }
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

    public boolean addPersistableNetworkPayload(PersistableNetworkPayload payload,
                                                @Nullable NodeAddress sender,
                                                boolean isDataOwner,
                                                boolean allowBroadcast,
                                                boolean reBroadcast,
                                                boolean checkDate) {
        log.debug("addPersistableNetworkPayload payload={}", payload);
        final byte[] hash = payload.getHash();
        if (payload.verifyHashSize()) {
            final ByteArray hashAsByteArray = new ByteArray(hash);
            boolean containsKey = getAppendOnlyDataStoreMap().containsKey(hashAsByteArray);
            if (!containsKey || reBroadcast) {
                if (!(payload instanceof DateTolerantPayload) || !checkDate || ((DateTolerantPayload) payload).isDateInTolerance()) {
                    if (!containsKey) {
                        appendOnlyDataStoreService.put(hashAsByteArray, payload);
                        appendOnlyDataStoreListeners.forEach(e -> e.onAdded(payload));
                    }
                    if (allowBroadcast)
                        broadcaster.broadcast(new AddPersistableNetworkPayloadMessage(payload), sender, null, isDataOwner);

                    return true;
                } else {
                    log.warn("Publish date of payload is not matching our current time and outside of our tolerance.\n" +
                            "Payload={}; now={}", payload.toString(), new Date());
                    return false;
                }
            } else {
                log.trace("We have that payload already in our map.");
                return false;
            }
        } else {
            log.warn("We got a hash exceeding our permitted size");
            return false;
        }
    }

    public boolean addProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender,
                                            @Nullable BroadcastHandler.Listener listener, boolean isDataOwner) {
        Log.traceCall("with allowBroadcast=true");
        return addProtectedStorageEntry(protectedStorageEntry, sender, listener, isDataOwner, true);
    }

    public boolean addProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender,
                                            @Nullable BroadcastHandler.Listener listener, boolean isDataOwner, boolean allowBroadcast) {
        Log.traceCall("with allowBroadcast=" + allowBroadcast);
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);
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
                hashMapChangedListeners.forEach(e -> e.onAdded(protectedStorageEntry));
                // printData("after add");
            } else {
                log.trace("We got that version of the data already, so we don't store it.");
            }

            if (hasSequenceNrIncreased) {
                sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.getSequenceNumber(), System.currentTimeMillis()));
                // We set the delay higher as we might receive a batch of items
                sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 2000);

                if (allowBroadcast)
                    broadcastProtectedStorageEntry(protectedStorageEntry, sender, listener, isDataOwner);
            } else {
                log.trace("We got that version of the data already, so we don't broadcast it.");
            }

            if (protectedStoragePayload instanceof PersistablePayload) {
                ByteArray compactHash = getCompactHashAsByteArray(protectedStoragePayload);
                ProtectedStorageEntry previous = protectedDataStoreService.putIfAbsent(compactHash, protectedStorageEntry);
                if (previous == null)
                    protectedDataStoreListeners.forEach(e -> e.onAdded(protectedStorageEntry));
            }
        } else {
            log.trace("add failed");
        }
        return result;
    }

    public void broadcastProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry,
                                               @Nullable NodeAddress sender,
                                               @Nullable BroadcastHandler.Listener broadcastListener,
                                               boolean isDataOwner) {
        broadcast(new AddDataMessage(protectedStorageEntry), sender, broadcastListener, isDataOwner);
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
                PublicKey ownerPubKey = storedData.getProtectedStoragePayload().getOwnerPubKey();
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
                    sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 1000);

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
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);
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
            sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 300);

            broadcast(new RemoveDataMessage(protectedStorageEntry), sender, null, isDataOwner);

            if (protectedStoragePayload instanceof PersistablePayload) {
                ByteArray compactHash = getCompactHashAsByteArray(protectedStoragePayload);
                ProtectedStorageEntry previous = protectedDataStoreService.remove(compactHash, protectedStorageEntry);
                if (previous != null) {
                    protectedDataStoreListeners.forEach(e -> e.onRemoved(protectedStorageEntry));
                } else {
                    log.info("We cannot remove the protectedStorageEntry from the persistedEntryMap as it does not exist.");
                }
            }
        } else {
            log.debug("remove failed");
        }
        return result;
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean removeMailboxData(ProtectedMailboxStorageEntry protectedMailboxStorageEntry, @Nullable NodeAddress sender, boolean isDataOwner) {
        Log.traceCall();
        ByteArray hashOfData = get32ByteHashAsByteArray(protectedMailboxStorageEntry.getProtectedStoragePayload());
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
            sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 300);

            broadcast(new RemoveMailboxDataMessage(protectedMailboxStorageEntry), sender, null, isDataOwner);
        } else {
            log.debug("removeMailboxData failed");
        }
        return result;
    }

    public ProtectedStorageEntry getProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfData = get32ByteHashAsByteArray(protectedStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedStorageEntry(protectedStoragePayload, ownerStoragePubKey.getPublic(), sequenceNumber, signature);
    }

    public RefreshOfferMessage getRefreshTTLMessage(ProtectedStoragePayload protectedStoragePayload, KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfPayload))
            sequenceNumber = sequenceNumberMap.get(hashOfPayload).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new RefreshOfferMessage(hashOfDataAndSeqNr, signature, hashOfPayload.bytes, sequenceNumber);
    }

    public ProtectedMailboxStorageEntry getMailboxDataWithSignedSeqNr(MailboxStoragePayload expirableMailboxStoragePayload,
                                                                      KeyPair storageSignaturePubKey, PublicKey receiversPublicKey)
            throws CryptoException {
        ByteArray hashOfData = get32ByteHashAsByteArray(expirableMailboxStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new DataAndSeqNrPair(expirableMailboxStoragePayload, sequenceNumber));
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

    public void addAppendOnlyDataStoreListener(AppendOnlyDataStoreListener listener) {
        appendOnlyDataStoreListeners.add(listener);
    }

    public void removeAppendOnlyDataStoreListener(AppendOnlyDataStoreListener listener) {
        appendOnlyDataStoreListeners.remove(listener);
    }

    public void addProtectedDataStoreListener(ProtectedDataStoreListener listener) {
        protectedDataStoreListeners.add(listener);
    }

    public void removeProtectedDataStoreListener(ProtectedDataStoreListener listener) {
        protectedDataStoreListeners.remove(listener);
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

    private boolean checkSignature(PublicKey ownerPubKey, byte[] hashOfDataAndSeqNr, byte[] signature) {
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
        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new DataAndSeqNrPair(protectedStorageEntry.getProtectedStoragePayload(), protectedStorageEntry.getSequenceNumber()));
        return checkSignature(protectedStorageEntry.getOwnerPubKey(), hashOfDataAndSeqNr, protectedStorageEntry.getSignature());
    }

    // Check that the pubkey of the storage entry matches the allowed pubkey for the addition or removal operation
    // in the contained mailbox message, or the pubKey of other kinds of network_messages.
    private boolean checkPublicKeys(ProtectedStorageEntry protectedStorageEntry, boolean isAddOperation) {
        boolean result;
        final ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        if (protectedStoragePayload instanceof MailboxStoragePayload) {
            MailboxStoragePayload payload = (MailboxStoragePayload) protectedStoragePayload;
            if (isAddOperation)
                result = payload.getSenderPubKeyForAddOperation() != null &&
                        payload.getSenderPubKeyForAddOperation().equals(protectedStorageEntry.getOwnerPubKey());
            else
                result = payload.getOwnerPubKey() != null &&
                        payload.getOwnerPubKey().equals(protectedStorageEntry.getOwnerPubKey());
        } else {
            result = protectedStorageEntry.getOwnerPubKey() != null &&
                    protectedStoragePayload != null &&
                    protectedStorageEntry.getOwnerPubKey().equals(protectedStoragePayload.getOwnerPubKey());
        }

        if (!result) {
            String res1 = protectedStorageEntry.toString();
            String res2 = "null";
            if (protectedStoragePayload != null &&
                    protectedStoragePayload.getOwnerPubKey() != null)
                res2 = Utilities.encodeToHex(protectedStoragePayload.getOwnerPubKey().getEncoded(), true);

            log.warn("PublicKey of payload data and ProtectedStorageEntry are not matching. protectedStorageEntry=" + res1 +
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
                    && get32ByteHashAsByteArray(entry.getProtectedStoragePayload()).equals(hashOfData);
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

    private ByteArray get32ByteHashAsByteArray(NetworkPayload data) {
        return new ByteArray(P2PDataStorage.get32ByteHash(data));
    }

    public static ByteArray getCompactHashAsByteArray(ProtectedStoragePayload protectedStoragePayload) {
        return new ByteArray(getCompactHash(protectedStoragePayload));
    }

    public static byte[] getCompactHash(ProtectedStoragePayload protectedStoragePayload) {
        return Hash.getSha256Ripemd160hash(protectedStoragePayload.toProtoMessage().toByteArray());
    }

    // Get a new map with entries older than PURGE_AGE_DAYS purged from the given map.
    private Map<ByteArray, MapValue> getPurgedSequenceNumberMap(Map<ByteArray, MapValue> persisted) {
        Map<ByteArray, MapValue> purged = new HashMap<>();
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
                    .map(e -> new Tuple2<>(org.bitcoinj.core.Utils.HEX.encode(get32ByteHashAsByteArray(e.getProtectedStoragePayload()).bytes), e))
                    .collect(Collectors.toList());
            tempList.sort((o1, o2) -> o1.first.compareTo(o2.first));
            tempList.stream().forEach(e -> {
                final ProtectedStorageEntry storageEntry = e.second;
                final ProtectedStoragePayload protectedStoragePayload = storageEntry.getProtectedStoragePayload();
                final MapValue mapValue = sequenceNumberMap.get(get32ByteHashAsByteArray(protectedStoragePayload));
                sb.append("\n")
                        .append("Hash=")
                        .append(e.first)
                        .append("; Class=")
                        .append(protectedStoragePayload.getClass().getSimpleName())
                        .append("; SequenceNumbers (Object/Stored)=")
                        .append(storageEntry.getSequenceNumber())
                        .append(" / ")
                        .append(mapValue != null ? mapValue.sequenceNr : "null")
                        .append("; TimeStamp (Object/Stored)=")
                        .append(storageEntry.getCreationTimeStamp())
                        .append(" / ")
                        .append(mapValue != null ? mapValue.timeStamp : "null")
                        .append("; Payload=")
                        .append(Utilities.toTruncatedString(protectedStoragePayload));
            });
            sb.append("\n------------------------------------------------------------\n");
            log.debug(sb.toString());
            log.debug("Data set " + info + " operation: size=" + map.values().size());
        }
    }

    /**
     * @param data
     * @return Hash of data
     */
    public static byte[] get32ByteHash(NetworkPayload data) {
        return Hash.getSha256Hash(data.toProtoMessage().toByteArray());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Static class
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Used as container for calculating cryptographic hash of data and sequenceNumber.
     */
    @EqualsAndHashCode
    @ToString
    public static final class DataAndSeqNrPair implements NetworkPayload {
        // data are only used for calculating cryptographic hash from both values so they are kept private
        private final ProtectedStoragePayload protectedStoragePayload;
        private final int sequenceNumber;

        public DataAndSeqNrPair(ProtectedStoragePayload protectedStoragePayload, int sequenceNumber) {
            this.protectedStoragePayload = protectedStoragePayload;
            this.sequenceNumber = sequenceNumber;
        }

        // Used only for calculating hash of byte array from PB object
        @Override
        public com.google.protobuf.Message toProtoMessage() {
            return PB.DataAndSeqNrPair.newBuilder()
                    .setPayload((PB.StoragePayload) protectedStoragePayload.toProtoMessage())
                    .setSequenceNumber(sequenceNumber)
                    .build();
        }
    }


    /**
     * Used as key object in map for cryptographic hash of stored data as byte[] as primitive data type cannot be
     * used as key
     */
    @EqualsAndHashCode
    public static final class ByteArray implements PersistablePayload {
        // That object is saved to disc. We need to take care of changes to not break deserialization.
        public final byte[] bytes;

        @Override
        public String toString() {
            return "ByteArray{" +
                    "bytes as Hex=" + Hex.toHexString(bytes) +
                    '}';
        }

        public ByteArray(byte[] bytes) {
            this.bytes = bytes;
        }


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Protobuffer
        ///////////////////////////////////////////////////////////////////////////////////////////

        public ByteArray(String hex) {
            this.bytes = Utilities.decodeFromHex(hex);
        }

        @Override
        public PB.ByteArray toProtoMessage() {
            return PB.ByteArray.newBuilder().setBytes(ByteString.copyFrom(bytes)).build();
        }

        public static ByteArray fromProto(PB.ByteArray proto) {
            return new ByteArray(proto.getBytes().toByteArray());
        }

        public String getHex() {
            return Utilities.encodeToHex(bytes);
        }

        public static Set<P2PDataStorage.ByteArray> convertBytesSetToByteArraySet(Set<byte[]> set) {
            return set != null ?
                    set.stream()
                            .map(P2PDataStorage.ByteArray::new)
                            .collect(Collectors.toSet())
                    : new HashSet<>();
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

