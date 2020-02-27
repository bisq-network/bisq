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
import bisq.network.p2p.peers.getdata.messages.GetDataRequest;
import bisq.network.p2p.peers.getdata.messages.GetDataResponse;
import bisq.network.p2p.peers.getdata.messages.GetUpdatedDataRequest;
import bisq.network.p2p.peers.getdata.messages.PreliminaryGetDataRequest;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.messages.AddOncePayload;
import bisq.network.p2p.storage.messages.AddPersistableNetworkPayloadMessage;
import bisq.network.p2p.storage.messages.BroadcastMessage;
import bisq.network.p2p.storage.messages.RefreshOfferMessage;
import bisq.network.p2p.storage.messages.RemoveDataMessage;
import bisq.network.p2p.storage.messages.RemoveMailboxDataMessage;
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import bisq.network.p2p.storage.payload.DateTolerantPayload;
import bisq.network.p2p.storage.payload.ExpirablePayload;
import bisq.network.p2p.storage.payload.ProcessOncePersistableNetworkPayload;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStoragePayload;
import bisq.network.p2p.storage.payload.RequiresOwnerIsOnlinePayload;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreListener;
import bisq.network.p2p.storage.persistence.AppendOnlyDataStoreService;
import bisq.network.p2p.storage.persistence.ProtectedDataStoreService;
import bisq.network.p2p.storage.persistence.ResourceDataStoreService;
import bisq.network.p2p.storage.persistence.SequenceNumberMap;

import bisq.common.Timer;
import bisq.common.UserThread;
import bisq.common.app.Capabilities;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.Hash;
import bisq.common.crypto.Sig;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.network.NetworkPayload;
import bisq.common.proto.persistable.PersistablePayload;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.storage.Storage;
import bisq.common.util.Hex;
import bisq.common.util.Tuple2;
import bisq.common.util.Utilities;

import com.google.protobuf.ByteString;

import com.google.inject.name.Named;

import javax.inject.Inject;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Maps;


import java.security.KeyPair;
import java.security.PublicKey;

import java.time.Clock;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    @VisibleForTesting
    public static final int PURGE_AGE_DAYS = 10;

    @VisibleForTesting
    public static final int CHECK_TTL_INTERVAL_SEC = 60;

    private boolean initialRequestApplied = false;

    private final Broadcaster broadcaster;
    private final AppendOnlyDataStoreService appendOnlyDataStoreService;
    private final ProtectedDataStoreService protectedDataStoreService;
    private final ResourceDataStoreService resourceDataStoreService;

    @Getter
    private final Map<ByteArray, ProtectedStorageEntry> map = new ConcurrentHashMap<>();
    private final Set<ByteArray> removedAddOncePayloads = new HashSet<>();
    private final Set<HashMapChangedListener> hashMapChangedListeners = new CopyOnWriteArraySet<>();
    private Timer removeExpiredEntriesTimer;

    private final Storage<SequenceNumberMap> sequenceNumberMapStorage;

    @VisibleForTesting
    final SequenceNumberMap sequenceNumberMap = new SequenceNumberMap();

    private final Set<AppendOnlyDataStoreListener> appendOnlyDataStoreListeners = new CopyOnWriteArraySet<>();
    private final Clock clock;

    /// The maximum number of items that must exist in the SequenceNumberMap before it is scheduled for a purge
    /// which removes entries after PURGE_AGE_DAYS.
    private final int maxSequenceNumberMapSizeBeforePurge;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public P2PDataStorage(NetworkNode networkNode,
                          Broadcaster broadcaster,
                          AppendOnlyDataStoreService appendOnlyDataStoreService,
                          ProtectedDataStoreService protectedDataStoreService,
                          ResourceDataStoreService resourceDataStoreService,
                          Storage<SequenceNumberMap> sequenceNumberMapStorage,
                          Clock clock,
                          @Named("MAX_SEQUENCE_NUMBER_MAP_SIZE_BEFORE_PURGE") int maxSequenceNumberBeforePurge) {
        this.broadcaster = broadcaster;
        this.appendOnlyDataStoreService = appendOnlyDataStoreService;
        this.protectedDataStoreService = protectedDataStoreService;
        this.resourceDataStoreService = resourceDataStoreService;
        this.clock = clock;
        this.maxSequenceNumberMapSizeBeforePurge = maxSequenceNumberBeforePurge;


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
        resourceDataStoreService.readFromResources(postFix);

        map.putAll(protectedDataStoreService.getMap());
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // RequestData API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Returns a PreliminaryGetDataRequest that can be sent to a peer node to request missing Payload data.
     */
    public PreliminaryGetDataRequest buildPreliminaryGetDataRequest(int nonce) {
        return new PreliminaryGetDataRequest(nonce, this.getKnownPayloadHashes());
    }

    /**
     * Returns a GetUpdatedDataRequest that can be sent to a peer node to request missing Payload data.
     */
    public GetUpdatedDataRequest buildGetUpdatedDataRequest(NodeAddress senderNodeAddress, int nonce) {
        return new GetUpdatedDataRequest(senderNodeAddress, nonce, this.getKnownPayloadHashes());
    }

    /**
     * Returns the set of known payload hashes. This is used in the GetData path to request missing data from peer nodes
     */
    private Set<byte[]> getKnownPayloadHashes() {
        // We collect the keys of the PersistableNetworkPayload items so we exclude them in our request.
        // PersistedStoragePayload items don't get removed, so we don't have an issue with the case that
        // an object gets removed in between PreliminaryGetDataRequest and the GetUpdatedDataRequest and we would
        // miss that event if we do not load the full set or use some delta handling.
        Set<byte[]> excludedKeys =this.appendOnlyDataStoreService.getMap().keySet().stream()
                .map(e -> e.bytes)
                .collect(Collectors.toSet());

        Set<byte[]> excludedKeysFromPersistedEntryMap = this.map.keySet()
                .stream()
                .map(e -> e.bytes)
                .collect(Collectors.toSet());

        excludedKeys.addAll(excludedKeysFromPersistedEntryMap);

        return excludedKeys;
    }

    /**
     * Generic function that can be used to filter a Map<ByteArray, ProtectedStorageEntry || PersistableNetworkPayload>
     * by a given set of keys and peer capabilities.
     */
    static private <T extends NetworkPayload> Set<T> filterKnownHashes(
            Map<ByteArray, T> toFilter,
            Function<T, ? extends NetworkPayload> objToPayload,
            Set<ByteArray> knownHashes,
            Capabilities peerCapabilities,
            int maxEntries,
            AtomicBoolean outTruncated) {

        AtomicInteger limit = new AtomicInteger(maxEntries);

        Set<T> filteredResults = toFilter.entrySet().stream()
                .filter(e -> !knownHashes.contains(e.getKey()))
                .filter(e -> limit.decrementAndGet() >= 0)
                .map(Map.Entry::getValue)
                .filter(networkPayload -> shouldTransmitPayloadToPeer(peerCapabilities,
                                                                      objToPayload.apply(networkPayload)))
                .collect(Collectors.toSet());

        if (limit.get() < 0)
            outTruncated.set(true);

        return filteredResults;
    }

    /**
     * Returns a GetDataResponse object that contains the Payloads known locally, but not remotely.
     */
    public GetDataResponse buildGetDataResponse(
            GetDataRequest getDataRequest,
            int maxEntriesPerType,
            AtomicBoolean outPersistableNetworkPayloadOutputTruncated,
            AtomicBoolean outProtectedStorageEntryOutputTruncated,
            Capabilities peerCapabilities) {

        Set<P2PDataStorage.ByteArray> excludedKeysAsByteArray =
                P2PDataStorage.ByteArray.convertBytesSetToByteArraySet(getDataRequest.getExcludedKeys());

        Set<PersistableNetworkPayload> filteredPersistableNetworkPayloads =
                filterKnownHashes(
                        this.appendOnlyDataStoreService.getMap(),
                        Function.identity(),
                        excludedKeysAsByteArray,
                        peerCapabilities,
                        maxEntriesPerType,
                        outPersistableNetworkPayloadOutputTruncated);

        Set<ProtectedStorageEntry> filteredProtectedStorageEntries =
                filterKnownHashes(
                        this.map,
                        ProtectedStorageEntry::getProtectedStoragePayload,
                        excludedKeysAsByteArray,
                        peerCapabilities,
                        maxEntriesPerType,
                        outProtectedStorageEntryOutputTruncated);

        return new GetDataResponse(
                filteredProtectedStorageEntries,
                filteredPersistableNetworkPayloads,
                getDataRequest.getNonce(),
                getDataRequest instanceof GetUpdatedDataRequest);
    }

    /**
     * Returns true if a Payload should be transmit to a peer given the peer's supported capabilities.
     */
    private static boolean shouldTransmitPayloadToPeer(Capabilities peerCapabilities, NetworkPayload payload) {

        // Sanity check to ensure this isn't used outside P2PDataStorage
        if (!(payload instanceof ProtectedStoragePayload || payload instanceof PersistableNetworkPayload))
            return false;

        // If the payload doesn't have a required capability, we should transmit it
        if (!(payload instanceof CapabilityRequiringPayload))
            return true;

        // Otherwise, only transmit the Payload if the peer supports all capabilities required by the payload
        boolean shouldTransmit = peerCapabilities.containsAll(((CapabilityRequiringPayload) payload).getRequiredCapabilities());

        if (!shouldTransmit) {
            log.debug("We do not send the message to the peer because they do not support the required capability for that message type.\n" +
                    "storagePayload is: " + Utilities.toTruncatedString(payload));
        }

        return shouldTransmit;
    }

    /**
     * Processes a GetDataResponse message and updates internal state. Does not broadcast updates to the P2P network
     * or domain listeners.
     */
    public void processGetDataResponse(GetDataResponse getDataResponse, NodeAddress sender) {
        final Set<ProtectedStorageEntry> dataSet = getDataResponse.getDataSet();
        Set<PersistableNetworkPayload> persistableNetworkPayloadSet = getDataResponse.getPersistableNetworkPayloadSet();

        long ts2 = System.currentTimeMillis();
        dataSet.forEach(e -> {
            // We don't broadcast here (last param) as we are only connected to the seed node and would be pointless
            addProtectedStorageEntry(e, sender, null, false);

        });
        log.info("Processing {} protectedStorageEntries took {} ms.", dataSet.size(), this.clock.millis() - ts2);

        ts2 = this.clock.millis();
        persistableNetworkPayloadSet.forEach(e -> {
            if (e instanceof ProcessOncePersistableNetworkPayload) {
                // We use an optimized method as many checks are not required in that case to avoid
                // performance issues.
                // Processing 82645 items took now 61 ms compared to earlier version where it took ages (> 2min).
                // Usually we only get about a few hundred or max. a few 1000 items. 82645 is all
                // trade stats stats and all account age witness data.

                // We only apply it once from first response
                if (!initialRequestApplied) {
                    addPersistableNetworkPayloadFromInitialRequest(e);

                }
            } else {
                // We don't broadcast here as we are only connected to the seed node and would be pointless
                addPersistableNetworkPayload(e, sender,false, false, false);
            }
        });
        log.info("Processing {} persistableNetworkPayloads took {} ms.",
                persistableNetworkPayloadSet.size(), this.clock.millis() - ts2);

        // We only process PersistableNetworkPayloads implementing ProcessOncePersistableNetworkPayload once. It can cause performance
        // issues and since the data is rarely out of sync it is not worth it to apply them from multiple peers during
        // startup.
        initialRequestApplied = true;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void shutDown() {
        if (removeExpiredEntriesTimer != null)
            removeExpiredEntriesTimer.stop();
    }

    @VisibleForTesting
    void removeExpiredEntries() {
        log.trace("removeExpiredEntries");
        // The moment when an object becomes expired will not be synchronous in the network and we could
        // get add network_messages after the object has expired. To avoid repeated additions of already expired
        // object when we get it sent from new peers, we don’t remove the sequence number from the map.
        // That way an ADD message for an already expired data will fail because the sequence number
        // is equal and not larger as expected.
        ArrayList<Map.Entry<ByteArray, ProtectedStorageEntry>> toRemoveList =
                map.entrySet().stream()
                .filter(entry -> entry.getValue().isExpired(this.clock))
                .collect(Collectors.toCollection(ArrayList::new));

        // Batch processing can cause performance issues, so do all of the removes first, then update the listeners
        // to let them know about the removes.
        toRemoveList.forEach(toRemoveItem -> {
            log.debug("We found an expired data entry. We remove the protectedData:\n\t" +
                    Utilities.toTruncatedString(toRemoveItem.getValue()));
        });
        removeFromMapAndDataStore(toRemoveList);

        if (sequenceNumberMap.size() > this.maxSequenceNumberMapSizeBeforePurge)
            sequenceNumberMap.setMap(getPurgedSequenceNumberMap(sequenceNumberMap.getMap()));
    }

    public void onBootstrapComplete() {
        removeExpiredEntriesTimer = UserThread.runPeriodically(this::removeExpiredEntries, CHECK_TTL_INTERVAL_SEC);
    }

    public Map<ByteArray, PersistableNetworkPayload> getAppendOnlyDataStoreMap() {
        return appendOnlyDataStoreService.getMap();
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // MessageListener implementation
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onMessage(NetworkEnvelope networkEnvelope, Connection connection) {
        if (networkEnvelope instanceof BroadcastMessage) {
            connection.getPeersNodeAddressOptional().ifPresent(peersNodeAddress -> {
                if (networkEnvelope instanceof AddDataMessage) {
                    addProtectedStorageEntry(((AddDataMessage) networkEnvelope).getProtectedStorageEntry(), peersNodeAddress, null, true);
                } else if (networkEnvelope instanceof RemoveDataMessage) {
                    remove(((RemoveDataMessage) networkEnvelope).getProtectedStorageEntry(), peersNodeAddress);
                } else if (networkEnvelope instanceof RemoveMailboxDataMessage) {
                    remove(((RemoveMailboxDataMessage) networkEnvelope).getProtectedMailboxStorageEntry(), peersNodeAddress);
                } else if (networkEnvelope instanceof RefreshOfferMessage) {
                    refreshTTL((RefreshOfferMessage) networkEnvelope, peersNodeAddress);
                } else if (networkEnvelope instanceof AddPersistableNetworkPayloadMessage) {
                    addPersistableNetworkPayload(((AddPersistableNetworkPayloadMessage) networkEnvelope).getPersistableNetworkPayload(),
                            peersNodeAddress, true, false, true);
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
        if (closeConnectionReason.isIntended)
            return;

        if (!connection.getPeersNodeAddressOptional().isPresent())
            return;

        NodeAddress peersNodeAddress = connection.getPeersNodeAddressOptional().get();

        // Backdate all the eligible payloads based on the node that disconnected
        map.values().stream()
                .filter(protectedStorageEntry -> protectedStorageEntry.getProtectedStoragePayload() instanceof RequiresOwnerIsOnlinePayload)
                .filter(protectedStorageEntry -> ((RequiresOwnerIsOnlinePayload) protectedStorageEntry.getProtectedStoragePayload()).getOwnerNodeAddress().equals(peersNodeAddress))
                .forEach(protectedStorageEntry ->  {
                    // We only set the data back by half of the TTL and remove the data only if is has
                    // expired after that back dating.
                    // We might get connection drops which are not caused by the node going offline, so
                    // we give more tolerance with that approach, giving the node the chance to
                    // refresh the TTL with a refresh message.
                    // We observed those issues during stress tests, but it might have been caused by the
                    // test set up (many nodes/connections over 1 router)
                    // TODO investigate what causes the disconnections.
                    // Usually the are: SOCKET_TIMEOUT ,TERMINATED (EOFException)
                    log.debug("Backdating {} due to closeConnectionReason={}", protectedStorageEntry, closeConnectionReason);
                    protectedStorageEntry.backDate();
                });
    }

    @Override
    public void onError(Throwable throwable) {

    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Client API
    ///////////////////////////////////////////////////////////////////////////////////////////

    /**
     * Adds a PersistableNetworkPayload to the local P2P data storage. If it does not already exist locally, it will
     * be broadcast to the P2P network.
     * @param payload PersistableNetworkPayload to add to the network
     * @param sender local NodeAddress, if available
     * @param allowReBroadcast <code>true</code> if the PersistableNetworkPayload should be rebroadcast even if it
     *                         already exists locally
     * @return <code>true</code> if the PersistableNetworkPayload passes all validation and exists in the P2PDataStore
     *         on completion
     */
    public boolean addPersistableNetworkPayload(PersistableNetworkPayload payload,
                                                @Nullable NodeAddress sender,
                                                boolean allowReBroadcast) {
        return addPersistableNetworkPayload(
                payload, sender, true, allowReBroadcast, false);
    }

    private boolean addPersistableNetworkPayload(PersistableNetworkPayload payload,
                                                 @Nullable NodeAddress sender,
                                                 boolean allowBroadcast,
                                                 boolean reBroadcast,
                                                 boolean checkDate) {
        log.trace("addPersistableNetworkPayload payload={}", payload);

        // Payload hash size does not match expectation for that type of message.
        if (!payload.verifyHashSize()) {
            log.warn("addPersistableNetworkPayload failed due to unexpected hash size");
            return false;
        }

        ByteArray hashAsByteArray = new ByteArray(payload.getHash());
        boolean payloadHashAlreadyInStore = getAppendOnlyDataStoreMap().containsKey(hashAsByteArray);

        // Store already knows about this payload. Ignore it unless the caller specifically requests a republish.
        if (payloadHashAlreadyInStore && !reBroadcast) {
            log.trace("addPersistableNetworkPayload failed due to duplicate payload");
            return false;
        }

        // DateTolerantPayloads are only checked for tolerance from the onMessage handler (checkDate == true). If not in
        // tolerance, ignore it.
        if (checkDate && payload instanceof DateTolerantPayload && !((DateTolerantPayload) payload).isDateInTolerance((clock))) {
            log.warn("addPersistableNetworkPayload failed due to payload time outside tolerance.\n" +
                    "Payload={}; now={}", payload.toString(), new Date());
            return false;
        }

        // Add the payload and publish the state update to the appendOnlyDataStoreListeners
        if (!payloadHashAlreadyInStore) {
            appendOnlyDataStoreService.put(hashAsByteArray, payload);
            appendOnlyDataStoreListeners.forEach(e -> e.onAdded(payload));
        }

        // Broadcast the payload if requested by caller
        if (allowBroadcast)
            broadcaster.broadcast(new AddPersistableNetworkPayloadMessage(payload), sender, null);

        return true;
    }

    // When we receive initial data we skip several checks to improve performance. We requested only missing entries so we
    // do not need to check again if the item is contained in the map, which is a bit slow as the map can be very large.
    // Overwriting an entry would be also no issue. We also skip notifying listeners as we get called before the domain
    // is ready so no listeners are set anyway. We might get called twice from a redundant call later, so listeners
    // might be added then but as we have the data already added calling them would be irrelevant as well.
    private void addPersistableNetworkPayloadFromInitialRequest(PersistableNetworkPayload payload) {
        byte[] hash = payload.getHash();
        if (payload.verifyHashSize()) {
            ByteArray hashAsByteArray = new ByteArray(hash);
            appendOnlyDataStoreService.put(hashAsByteArray, payload);
        } else {
            log.warn("We got a hash exceeding our permitted size");
        }
    }

    /**
     * Adds a ProtectedStorageEntry to the local P2P data storage. If it does not already exist locally, it will be
     * broadcast to the P2P network.
     *
     * @param protectedStorageEntry ProtectedStorageEntry to add to the network
     * @param sender local NodeAddress, if available
     * @param listener optional listener that can be used to receive events on broadcast
     * @return <code>true</code> if the ProtectedStorageEntry was added to the local P2P data storage and broadcast
     */
    public boolean addProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry, @Nullable NodeAddress sender,
                                            @Nullable BroadcastHandler.Listener listener) {
        return addProtectedStorageEntry(protectedStorageEntry, sender, listener, true);
    }

    private boolean addProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry,
                                            @Nullable NodeAddress sender,
                                            @Nullable BroadcastHandler.Listener listener,
                                            boolean allowBroadcast) {
        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);

        if (protectedStoragePayload instanceof AddOncePayload &&
                removedAddOncePayloads.contains(hashOfPayload)) {
            log.warn("We have already removed that AddOncePayload by a previous removeDataMessage. " +
                    "We ignore that message. ProtectedStoragePayload: {}", protectedStoragePayload.toString());
            return false;
        }

        ProtectedStorageEntry storedEntry = map.get(hashOfPayload);

        // If we have seen a more recent operation for this payload and we have a payload locally, ignore it
        if (storedEntry != null &&
                !hasSequenceNrIncreased(protectedStorageEntry.getSequenceNumber(), hashOfPayload)) {
            return false;
        }

        // We want to allow add operations for equal sequence numbers if we don't have the payload locally. This is
        // the case for non-persistent Payloads that need to be reconstructed from peer and seed nodes each startup.
        MapValue sequenceNumberMapValue = sequenceNumberMap.get(hashOfPayload);
        if (sequenceNumberMapValue != null &&
                protectedStorageEntry.getSequenceNumber() < sequenceNumberMapValue.sequenceNr) {
            return false;
        }

        // Verify the ProtectedStorageEntry is well formed and valid for the add operation
        if (!protectedStorageEntry.isValidForAddOperation())
            return false;

        // If we have already seen an Entry with the same hash, verify the metadata is equal
        if (storedEntry != null && !protectedStorageEntry.matchesRelevantPubKey(storedEntry))
            return false;

        // This is an updated entry. Record it and signal listeners.
        map.put(hashOfPayload, protectedStorageEntry);
        hashMapChangedListeners.forEach(e -> e.onAdded(Collections.singletonList(protectedStorageEntry)));

        // Record the updated sequence number and persist it. Higher delay so we can batch more items.
        sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.getSequenceNumber(), this.clock.millis()));
        sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 2000);

        // Optionally, broadcast the add/update depending on the calling environment
        if (allowBroadcast)
            broadcaster.broadcast(new AddDataMessage(protectedStorageEntry), sender, listener);

        // Persist ProtectedStorageEntrys carrying PersistablePayload payloads
        if (protectedStoragePayload instanceof PersistablePayload)
            protectedDataStoreService.put(hashOfPayload, protectedStorageEntry);

        return true;
    }

    /**
     * Updates a local RefreshOffer with TTL changes and broadcasts those changes to the network
     *
     * @param refreshTTLMessage refreshTTLMessage containing the update
     * @param sender local NodeAddress, if available
     * @return <code>true</code> if the RefreshOffer was successfully updated and changes broadcast
     */
    public boolean refreshTTL(RefreshOfferMessage refreshTTLMessage,
                              @Nullable NodeAddress sender) {

        ByteArray hashOfPayload = new ByteArray(refreshTTLMessage.getHashOfPayload());
        ProtectedStorageEntry storedData = map.get(hashOfPayload);

        if (storedData == null) {
            log.debug("We don't have data for that refresh message in our map. That is expected if we missed the data publishing.");

            return false;
        }

        ProtectedStorageEntry storedEntry = map.get(hashOfPayload);
        ProtectedStorageEntry updatedEntry = new ProtectedStorageEntry(
                storedEntry.getProtectedStoragePayload(),
                storedEntry.getOwnerPubKey(),
                refreshTTLMessage.getSequenceNumber(),
                refreshTTLMessage.getSignature(),
                this.clock);


        // If we have seen a more recent operation for this payload, we ignore the current one
        if(!hasSequenceNrIncreased(updatedEntry.getSequenceNumber(), hashOfPayload))
            return false;

        // Verify the updated ProtectedStorageEntry is well formed and valid for update
        if (!updatedEntry.isValidForAddOperation())
            return false;

        // Update the hash map with the updated entry
        map.put(hashOfPayload, updatedEntry);

        // Record the latest sequence number and persist it
        sequenceNumberMap.put(hashOfPayload, new MapValue(updatedEntry.getSequenceNumber(), this.clock.millis()));
        sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 1000);

        // Always broadcast refreshes
        broadcaster.broadcast(refreshTTLMessage, sender, null);

        return true;
    }

    /**
     * Removes a ProtectedStorageEntry from the local P2P data storage. If it is successful, it will broadcast that
     * change to the P2P network.
     *
     * @param protectedStorageEntry ProtectedStorageEntry to add to the network
     * @param sender local NodeAddress, if available
     * @return <code>true</code> if the ProtectedStorageEntry was removed from the local P2P data storage and broadcast
     */
    public boolean remove(ProtectedStorageEntry protectedStorageEntry,
                          @Nullable NodeAddress sender) {
        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);

        // If we have seen a more recent operation for this payload, ignore this one
        if (!hasSequenceNrIncreased(protectedStorageEntry.getSequenceNumber(), hashOfPayload))
            return false;

        // Verify the ProtectedStorageEntry is well formed and valid for the remove operation
        if (!protectedStorageEntry.isValidForRemoveOperation())
            return false;

        // If we have already seen an Entry with the same hash, verify the metadata is the same
        ProtectedStorageEntry storedEntry = map.get(hashOfPayload);
        if (storedEntry != null && !protectedStorageEntry.matchesRelevantPubKey(storedEntry))
            return false;

        // Record the latest sequence number and persist it
        sequenceNumberMap.put(hashOfPayload, new MapValue(protectedStorageEntry.getSequenceNumber(), this.clock.millis()));
        sequenceNumberMapStorage.queueUpForSave(SequenceNumberMap.clone(sequenceNumberMap), 300);

        // Update that we have seen this AddOncePayload so the next time it is seen it fails verification
        if (protectedStoragePayload instanceof AddOncePayload)
            removedAddOncePayloads.add(hashOfPayload);

        if (storedEntry != null) {
            // Valid remove entry, do the remove and signal listeners
            removeFromMapAndDataStore(protectedStorageEntry, hashOfPayload);
        } /* else {
            // This means the RemoveData or RemoveMailboxData was seen prior to the AddData. We have already updated
            // the SequenceNumberMap appropriately so the stale Add will not pass validation, but we still want to
            // broadcast the remove to peers so they can update their state appropriately
        } */
        printData("after remove");

        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
            broadcaster.broadcast(new RemoveMailboxDataMessage((ProtectedMailboxStorageEntry) protectedStorageEntry), sender, null);
        } else {
            broadcaster.broadcast(new RemoveDataMessage(protectedStorageEntry), sender, null);
        }

        return true;
}


    /**
     * This method must be called only from client code not from network messages! We omit the ownership checks
     * so we must apply it only if it comes from our trusted application code. It is used from client code which detects
     * that the domain object violates specific domain rules.
     * We could make it more generic by adding an Interface with a generic validation method.
     *
     * @param protectedStorageEntry     The entry to be removed
     */
    public void removeInvalidProtectedStorageEntry(ProtectedStorageEntry protectedStorageEntry) {
        log.warn("We remove an invalid protectedStorageEntry: {}", protectedStorageEntry);
        ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
        ByteArray hashOfPayload = get32ByteHashAsByteArray(protectedStoragePayload);

        if (!map.containsKey(hashOfPayload)) {
            return;
        }

        removeFromMapAndDataStore(protectedStorageEntry, hashOfPayload);

        // We do not update the sequence number as that method is only called if we have received an invalid
        // protectedStorageEntry from a previous add operation.

        // We do not call maybeAddToRemoveAddOncePayloads to avoid that an invalid object might block a valid object
        // which we might receive in future (could be potential attack).

        // We do not broadcast as this is a local operation only to avoid our maps get polluted with invalid objects
        // and as we do not check for ownership a node would not accept such a procedure if it would come from untrusted
        // source (network).
    }

    public ProtectedStorageEntry getProtectedStorageEntry(ProtectedStoragePayload protectedStoragePayload,
                                                          KeyPair ownerStoragePubKey)
            throws CryptoException {
        ByteArray hashOfData = get32ByteHashAsByteArray(protectedStoragePayload);
        int sequenceNumber;
        if (sequenceNumberMap.containsKey(hashOfData))
            sequenceNumber = sequenceNumberMap.get(hashOfData).sequenceNr + 1;
        else
            sequenceNumber = 1;

        byte[] hashOfDataAndSeqNr = P2PDataStorage.get32ByteHash(new DataAndSeqNrPair(protectedStoragePayload, sequenceNumber));
        byte[] signature = Sig.sign(ownerStoragePubKey.getPrivate(), hashOfDataAndSeqNr);
        return new ProtectedStorageEntry(protectedStoragePayload, ownerStoragePubKey.getPublic(), sequenceNumber, signature, this.clock);
    }

    public RefreshOfferMessage getRefreshTTLMessage(ProtectedStoragePayload protectedStoragePayload,
                                                    KeyPair ownerStoragePubKey)
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
                                                                      KeyPair storageSignaturePubKey,
                                                                      PublicKey receiversPublicKey)
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
                storageSignaturePubKey.getPublic(), sequenceNumber, signature, receiversPublicKey, this.clock);
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

    @SuppressWarnings("unused")
    public void removeAppendOnlyDataStoreListener(AppendOnlyDataStoreListener listener) {
        appendOnlyDataStoreListeners.remove(listener);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void removeFromMapAndDataStore(ProtectedStorageEntry protectedStorageEntry, ByteArray hashOfPayload) {
        removeFromMapAndDataStore(Collections.singletonList(Maps.immutableEntry(hashOfPayload, protectedStorageEntry)));
    }

    private void removeFromMapAndDataStore(
            Collection<Map.Entry<ByteArray, ProtectedStorageEntry>> entriesToRemoveWithPayloadHash) {

        if (entriesToRemoveWithPayloadHash.isEmpty())
            return;

        ArrayList<ProtectedStorageEntry> entriesForSignal = new ArrayList<>(entriesToRemoveWithPayloadHash.size());
        entriesToRemoveWithPayloadHash.forEach(entryToRemoveWithPayloadHash -> {
            ByteArray hashOfPayload = entryToRemoveWithPayloadHash.getKey();
            ProtectedStorageEntry protectedStorageEntry = entryToRemoveWithPayloadHash.getValue();

            map.remove(hashOfPayload);
            entriesForSignal.add(protectedStorageEntry);

            ProtectedStoragePayload protectedStoragePayload = protectedStorageEntry.getProtectedStoragePayload();
            if (protectedStoragePayload instanceof PersistablePayload) {
                ProtectedStorageEntry previous = protectedDataStoreService.remove(hashOfPayload, protectedStorageEntry);
                if (previous == null)
                    log.error("We cannot remove the protectedStorageEntry from the persistedEntryMap as it does not exist.");
            }
        });

        hashMapChangedListeners.forEach(e -> e.onRemoved(entriesForSignal));
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
                log.trace(msg);
                return false;
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

    public static ByteArray get32ByteHashAsByteArray(NetworkPayload data) {
        return new ByteArray(P2PDataStorage.get32ByteHash(data));
    }

    // Get a new map with entries older than PURGE_AGE_DAYS purged from the given map.
    private Map<ByteArray, MapValue> getPurgedSequenceNumberMap(Map<ByteArray, MapValue> persisted) {
        Map<ByteArray, MapValue> purged = new HashMap<>();
        long maxAgeTs = this.clock.millis() - TimeUnit.DAYS.toMillis(PURGE_AGE_DAYS);
        persisted.forEach((key, value) -> {
            if (value.timeStamp > maxAgeTs)
                purged.put(key, value);
        });
        return purged;
    }

    private void printData(String info) {
        if (log.isTraceEnabled()) {
            StringBuilder sb = new StringBuilder("\n\n------------------------------------------------------------\n");
            sb.append("Data set ").append(info).append(" operation");
            // We print the items sorted by hash with the payload class name and id
            List<Tuple2<String, ProtectedStorageEntry>> tempList = map.values().stream()
                    .map(e -> new Tuple2<>(org.bitcoinj.core.Utils.HEX.encode(get32ByteHashAsByteArray(e.getProtectedStoragePayload()).bytes), e))
                    .sorted(Comparator.comparing(o -> o.first))
                    .collect(Collectors.toList());
            tempList.forEach(e -> {
                ProtectedStorageEntry storageEntry = e.second;
                ProtectedStoragePayload protectedStoragePayload = storageEntry.getProtectedStoragePayload();
                MapValue mapValue = sequenceNumberMap.get(get32ByteHashAsByteArray(protectedStoragePayload));
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
            log.trace(sb.toString());
            //log.debug("Data set " + info + " operation: size=" + map.values().size());
        }
    }

    /**
     * @param data Network payload
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
            return protobuf.DataAndSeqNrPair.newBuilder()
                    .setPayload((protobuf.StoragePayload) protectedStoragePayload.toProtoMessage())
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
                    "bytes as Hex=" + Hex.encode(bytes) +
                    '}';
        }

        public ByteArray(byte[] bytes) {
            this.bytes = bytes;
        }


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Protobuffer
        ///////////////////////////////////////////////////////////////////////////////////////////

        @Override
        public protobuf.ByteArray toProtoMessage() {
            return protobuf.ByteArray.newBuilder().setBytes(ByteString.copyFrom(bytes)).build();
        }

        public static ByteArray fromProto(protobuf.ByteArray proto) {
            return new ByteArray(proto.getBytes().toByteArray());
        }


        ///////////////////////////////////////////////////////////////////////////////////////////
        // Util
        ///////////////////////////////////////////////////////////////////////////////////////////

        @SuppressWarnings("unused")
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

        MapValue(int sequenceNr, long timeStamp) {
            this.sequenceNr = sequenceNr;
            this.timeStamp = timeStamp;
        }

        @Override
        public protobuf.MapValue toProtoMessage() {
            return protobuf.MapValue.newBuilder().setSequenceNr(sequenceNr).setTimeStamp(timeStamp).build();
        }

        public static MapValue fromProto(protobuf.MapValue proto) {
            return new MapValue(proto.getSequenceNr(), proto.getTimeStamp());
        }
    }
}

