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

package bisq.network.p2p.mailbox;

import bisq.network.crypto.EncryptionService;
import bisq.network.p2p.DecryptedMessageWithPubKey;
import bisq.network.p2p.NetworkNotReadyException;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.PrefixedSealedAndSignedMessage;
import bisq.network.p2p.SendMailboxMessageListener;
import bisq.network.p2p.messaging.DecryptedMailboxListener;
import bisq.network.p2p.network.Connection;
import bisq.network.p2p.network.NetworkNode;
import bisq.network.p2p.peers.BroadcastHandler;
import bisq.network.p2p.peers.Broadcaster;
import bisq.network.p2p.peers.PeerManager;
import bisq.network.p2p.storage.HashMapChangedListener;
import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.messages.AddDataMessage;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;
import bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import bisq.network.utils.CapabilityUtils;

import bisq.common.UserThread;
import bisq.common.config.Config;
import bisq.common.crypto.CryptoException;
import bisq.common.crypto.KeyRing;
import bisq.common.crypto.PubKeyRing;
import bisq.common.crypto.SealedAndSigned;
import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.ProtobufferException;
import bisq.common.proto.network.NetworkEnvelope;
import bisq.common.proto.persistable.PersistedDataHost;
import bisq.common.util.Utilities;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import com.google.common.base.Joiner;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.SettableFuture;

import java.security.PublicKey;

import java.time.Clock;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Responsible for handling of mailbox messages.
 * We store mailbox messages locally to cause less load at initial data requests (using excluded keys) and to get better
 * resilience in case processing failed. In such cases it would be re-applied after restart.
 *
 * We use a map with the uid of the decrypted mailboxMessage if it was our own mailbox message. Otherwise we use the uid of the
 * prefixedSealedAndSignedMessage if it was a mailboxMessage not addressed to us. It would be better to use the hash of the payload but that
 * would require a large refactoring in the trade protocol. We call the remove method after a message got processed and pass
 * the tradeMessage to the remove method. We do not have the outer envelope which would be needed for the hash and
 * we do not want to pass around that to all trade methods just for that use case. So we use the uid as lookup to get
 * the mailboxItem containing the data we need for removal.
 *
 * If a node was not online and the remove mailbox message was sent during that time, the persisted mailbox message
 * does not get removed. So we need to take care that the persisted data is not growing too much and we apply some
 * filtering and limiting at reading the persisted data.
 * Any message gets removed once the expiry data (max 15 days) is reached. Missing messages would be delivered from
 * initial data requests.
 */
@Singleton
@Slf4j
public class MailboxMessageService implements HashMapChangedListener, PersistedDataHost {
    private static final long REPUBLISH_DELAY_SEC = TimeUnit.MINUTES.toSeconds(2);

    private final NetworkNode networkNode;
    private final PeerManager peerManager;
    private final P2PDataStorage p2PDataStorage;
    private final EncryptionService encryptionService;
    private final IgnoredMailboxService ignoredMailboxService;
    private final PersistenceManager<MailboxMessageList> persistenceManager;
    private final KeyRing keyRing;
    private final Clock clock;
    private final boolean republishMailboxEntries;

    private final Set<DecryptedMailboxListener> decryptedMailboxListeners = new CopyOnWriteArraySet<>();
    private final MailboxMessageList mailboxMessageList = new MailboxMessageList();
    private final Map<String, MailboxItem> mailboxItemsByUid = new HashMap<>();

    private boolean isBootstrapped;

    @Inject
    public MailboxMessageService(NetworkNode networkNode,
                                 PeerManager peerManager,
                                 P2PDataStorage p2PDataStorage,
                                 EncryptionService encryptionService,
                                 IgnoredMailboxService ignoredMailboxService,
                                 PersistenceManager<MailboxMessageList> persistenceManager,
                                 KeyRing keyRing,
                                 Clock clock,
                                 @Named(Config.REPUBLISH_MAILBOX_ENTRIES) boolean republishMailboxEntries) {
        this.networkNode = networkNode;
        this.peerManager = peerManager;
        this.p2PDataStorage = p2PDataStorage;
        this.encryptionService = encryptionService;
        this.ignoredMailboxService = ignoredMailboxService;
        this.persistenceManager = persistenceManager;
        this.keyRing = keyRing;
        this.clock = clock;
        this.republishMailboxEntries = republishMailboxEntries;

        this.persistenceManager.initialize(mailboxMessageList, PersistenceManager.Source.PRIVATE_LOW_PRIO);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    log.trace("## readPersisted persisted {}", persisted.size());
                    Map<String, Long> numItemsPerDay = new HashMap<>();
                    // We sort by creation date and limit to max 3000 entries, so oldest items get skipped even if TTL
                    // is not reached to cap the memory footprint. 3000 items is about 10 MB.
                    persisted.stream()
                            .sorted(Comparator.comparingLong(o -> ((MailboxItem) o).getProtectedMailboxStorageEntry().getCreationTimeStamp()).reversed())
                            .limit(3000)
                            .filter(e -> !e.isExpired(clock))
                            .filter(e -> !mailboxItemsByUid.containsKey(e.getUid()))
                            .forEach(mailboxItem -> {
                                ProtectedMailboxStorageEntry protectedMailboxStorageEntry = mailboxItem.getProtectedMailboxStorageEntry();
                                int serializedSize = protectedMailboxStorageEntry.toProtoMessage().getSerializedSize();
                                // Usual size is 3-4kb. A few are about 15kb and very few are larger and about 100kb or
                                // more (probably attachments in disputes)
                                // We ignore those large data to reduce memory footprint.
                                if (serializedSize < 20000) {
                                    String date = new Date(protectedMailboxStorageEntry.getCreationTimeStamp()).toString();
                                    String day = date.substring(4, 10);
                                    numItemsPerDay.putIfAbsent(day, 0L);
                                    numItemsPerDay.put(day, numItemsPerDay.get(day) + 1);

                                    String uid = mailboxItem.getUid();
                                    mailboxItemsByUid.put(uid, mailboxItem);
                                    mailboxMessageList.add(mailboxItem);

                                    // We add it to our map so that it get added to the excluded key set we send for
                                    // the initial data requests. So that helps to lower the load for mailbox messages at
                                    // initial data requests.
                                    //todo check if listeners are called too early
                                    p2PDataStorage.addProtectedMailboxStorageEntryToMap(protectedMailboxStorageEntry);

                                    log.trace("## readPersisted uid={}\nhash={}\nisMine={}\ndate={}\nsize={}",
                                            uid,
                                            P2PDataStorage.get32ByteHashAsByteArray(protectedMailboxStorageEntry.getProtectedStoragePayload()),
                                            mailboxItem.isMine(),
                                            date,
                                            serializedSize);
                                }
                            });

                    List<Map.Entry<String, Long>> perDay = numItemsPerDay.entrySet().stream()
                            .sorted(Map.Entry.comparingByKey())
                            .collect(Collectors.toList());
                    log.info("We loaded {} persisted mailbox messages.\nPer day distribution:\n{}", mailboxMessageList.size(), Joiner.on("\n").join(perDay));
                    requestPersistence();
                    completeHandler.run();
                },
                completeHandler);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    // We don't listen on requestDataManager directly as we require the correct
    // order of execution. The p2pService is handling the correct order of execution and we get called
    // directly from there.
    public void onBootstrapped() {
        if (!isBootstrapped) {
            isBootstrapped = true;
        }
    }

    // second stage starup for MailboxMessageService ... apply existing messages to their modules
    public void initAfterBootstrapped() {
        // Only now we start listening and processing. The p2PDataStorage is our cache for data we have received
        // after the hidden service was ready.
        addHashMapChangedListener();
        onAdded(p2PDataStorage.getMap().values());
        maybeRepublishMailBoxMessages();
    }


    public void sendEncryptedMailboxMessage(NodeAddress peer,
                                            PubKeyRing peersPubKeyRing,
                                            MailboxMessage mailboxMessage,
                                            SendMailboxMessageListener sendMailboxMessageListener) {
        if (peersPubKeyRing == null) {
            log.debug("sendEncryptedMailboxMessage: peersPubKeyRing is null. We ignore the call.");
            return;
        }

        checkNotNull(peer, "PeerAddress must not be null (sendEncryptedMailboxMessage)");
        checkNotNull(networkNode.getNodeAddress(),
                "My node address must not be null at sendEncryptedMailboxMessage");
        checkArgument(!keyRing.getPubKeyRing().equals(peersPubKeyRing), "We got own keyring instead of that from peer");

        if (!isBootstrapped) {
            throw new NetworkNotReadyException();
        }

        if (networkNode.getAllConnections().isEmpty()) {
            sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                    "Please check your internet connection.");
            return;
        }

        NetworkEnvelope networkEnvelope = (NetworkEnvelope) mailboxMessage;
        if (CapabilityUtils.capabilityRequiredAndCapabilityNotSupported(peer, networkEnvelope, peerManager)) {
            sendMailboxMessageListener.onFault("We did not send the EncryptedMailboxMessage " +
                    "because the peer does not support the capability.");
            return;
        }

        try {
            PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = new PrefixedSealedAndSignedMessage(
                    networkNode.getNodeAddress(),
                    encryptionService.encryptAndSign(peersPubKeyRing, networkEnvelope));
            SettableFuture<Connection> future = networkNode.sendMessage(peer, prefixedSealedAndSignedMessage);
            Futures.addCallback(future, new FutureCallback<>() {
                @Override
                public void onSuccess(@Nullable Connection connection) {
                    sendMailboxMessageListener.onArrived();
                }

                @Override
                public void onFailure(@NotNull Throwable throwable) {
                    PublicKey receiverStoragePublicKey = peersPubKeyRing.getSignaturePubKey();
                    long ttl = mailboxMessage.getTTL();
                    log.trace("## We take TTL from {}. ttl={}", mailboxMessage.getClass().getSimpleName(), ttl);
                    addMailboxData(new MailboxStoragePayload(prefixedSealedAndSignedMessage,
                                    keyRing.getSignatureKeyPair().getPublic(),
                                    receiverStoragePublicKey,
                                    ttl),
                            receiverStoragePublicKey,
                            sendMailboxMessageListener);
                }
            }, MoreExecutors.directExecutor());
        } catch (CryptoException e) {
            log.error("sendEncryptedMessage failed");
            e.printStackTrace();
            sendMailboxMessageListener.onFault("sendEncryptedMailboxMessage failed " + e);
        }
    }

    /**
     * The mailboxMessage has been applied and we remove it from our local storage and from the network.
     *
     * @param mailboxMessage The MailboxMessage to be removed
     */
    public void removeMailboxMsg(MailboxMessage mailboxMessage) {
        if (isBootstrapped) {
            // We need to delay a bit to not get a ConcurrentModificationException as we might iterate over
            // mailboxMessageList while getting called.
            UserThread.execute(() -> {
                String uid = mailboxMessage.getUid();
                if (!mailboxItemsByUid.containsKey(uid)) {
                    return;
                }

                // We called removeMailboxEntryFromNetwork at processMyMailboxItem,
                // but in case we have not been bootstrapped at that moment it did not get removed from the network.
                // So to be sure it gets removed we try to remove it now again.
                // In case it was removed earlier it will return early anyway inside the p2pDataStorage.
                removeMailboxEntryFromNetwork(mailboxItemsByUid.get(uid).getProtectedMailboxStorageEntry());

                // We will get called the onRemoved handler which triggers removeMailboxItemFromMap as well.
                // But as we use the uid from the decrypted data which is not available at onRemoved we need to
                // call removeMailboxItemFromMap here. The onRemoved only removes foreign mailBoxMessages.
                log.trace("## removeMailboxMsg uid={}", uid);
                removeMailboxItemFromLocalStore(uid);
            });
        } else {
            // In case the network was not ready yet we try again later
            UserThread.runAfter(() -> removeMailboxMsg(mailboxMessage), 30);
        }
    }

    public Set<DecryptedMessageWithPubKey> getMyDecryptedMailboxMessages() {
        return mailboxItemsByUid.values().stream()
                .filter(MailboxItem::isMine)
                .map(MailboxItem::getDecryptedMessageWithPubKey)
                .collect(Collectors.toSet());
    }

    public void addDecryptedMailboxListener(DecryptedMailboxListener listener) {
        decryptedMailboxListeners.add(listener);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // HashMapChangedListener implementation for ProtectedStorageEntry items
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void onAdded(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        log.trace("## onAdded");
        Collection<ProtectedMailboxStorageEntry> entries = protectedStorageEntries.stream()
                .filter(e -> e instanceof ProtectedMailboxStorageEntry)
                .map(e -> (ProtectedMailboxStorageEntry) e)
                .filter(e -> networkNode.getNodeAddress() != null)
                .collect(Collectors.toSet());
        if (entries.size() > 1) {
            threadedBatchProcessMailboxEntries(entries);
        } else if (entries.size() == 1) {
            processSingleMailboxEntry(entries);
        }
    }

    @Override
    public void onRemoved(Collection<ProtectedStorageEntry> protectedStorageEntries) {
        log.trace("## onRemoved");
        // We can only remove the foreign mailbox messages as for our own we use the uid from the decrypted
        // payload which is not available here. But own mailbox messages get removed anyway after processing
        // at the removeMailboxMsg method.
        protectedStorageEntries.stream()
                .filter(protectedStorageEntry -> protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
                .map(protectedStorageEntry -> (ProtectedMailboxStorageEntry) protectedStorageEntry)
                .map(e -> e.getMailboxStoragePayload().getPrefixedSealedAndSignedMessage().getUid())
                .filter(mailboxItemsByUid::containsKey)
                .forEach(this::removeMailboxItemFromLocalStore);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Private
    ///////////////////////////////////////////////////////////////////////////////////////////

    private void addHashMapChangedListener() {
        p2PDataStorage.addHashMapChangedListener(this);
    }

    private void processSingleMailboxEntry(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        checkArgument(protectedMailboxStorageEntries.size() == 1);
        var mailboxItems = new ArrayList<>(getMailboxItems(protectedMailboxStorageEntries));
        if (mailboxItems.size() == 1) {
            handleMailboxItem(mailboxItems.get(0));
        }
    }

    // We run the batch processing of all mailbox messages we have received at startup in a thread to not block the UI.
    // For about 1000 messages decryption takes about 1 sec.
    private void threadedBatchProcessMailboxEntries(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        ListeningExecutorService executor = Utilities.getSingleThreadListeningExecutor("processMailboxEntry-" + new Random().nextInt(1000));
        long ts = System.currentTimeMillis();
        ListenableFuture<Set<MailboxItem>> future = executor.submit(() -> {
            var mailboxItems = getMailboxItems(protectedMailboxStorageEntries);
            log.info("Batch processing of {} mailbox entries took {} ms",
                    protectedMailboxStorageEntries.size(),
                    System.currentTimeMillis() - ts);
            return mailboxItems;
        });

        Futures.addCallback(future, new FutureCallback<>() {
            public void onSuccess(Set<MailboxItem> decryptedMailboxMessageWithEntries) {
                UserThread.execute(() -> decryptedMailboxMessageWithEntries.forEach(e -> handleMailboxItem(e)));
            }

            public void onFailure(@NotNull Throwable throwable) {
                log.error(throwable.toString());
            }
        }, MoreExecutors.directExecutor());
    }

    private Set<MailboxItem> getMailboxItems(Collection<ProtectedMailboxStorageEntry> protectedMailboxStorageEntries) {
        Set<MailboxItem> mailboxItems = new HashSet<>();
        protectedMailboxStorageEntries.stream()
                .map(this::tryDecryptProtectedMailboxStorageEntry)
                .forEach(mailboxItems::add);
        return mailboxItems;
    }

    private MailboxItem tryDecryptProtectedMailboxStorageEntry(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        PrefixedSealedAndSignedMessage prefixedSealedAndSignedMessage = protectedMailboxStorageEntry
                .getMailboxStoragePayload()
                .getPrefixedSealedAndSignedMessage();
        SealedAndSigned sealedAndSigned = prefixedSealedAndSignedMessage.getSealedAndSigned();
        String uid = prefixedSealedAndSignedMessage.getUid();
        if (ignoredMailboxService.isIgnored(uid)) {
            // We had persisted a past failed decryption attempt on that message so we don't try again and return early
            return new MailboxItem(protectedMailboxStorageEntry, null);
        }
        try {
            DecryptedMessageWithPubKey decryptedMessageWithPubKey = encryptionService.decryptAndVerify(sealedAndSigned);
            checkArgument(decryptedMessageWithPubKey.getNetworkEnvelope() instanceof MailboxMessage);
            return new MailboxItem(protectedMailboxStorageEntry, decryptedMessageWithPubKey);
        } catch (CryptoException ignore) {
            // Expected if message was not intended for us
            // We persist those entries so at the next startup we do not need to try to decrypt it anymore
            ignoredMailboxService.ignore(uid, protectedMailboxStorageEntry.getCreationTimeStamp());
        } catch (ProtobufferException e) {
            log.error(e.toString());
            e.getStackTrace();
        }
        return new MailboxItem(protectedMailboxStorageEntry, null);
    }

    private void handleMailboxItem(MailboxItem mailboxItem) {
        String uid = mailboxItem.getUid();
        if (!mailboxItemsByUid.containsKey(uid)) {
            mailboxItemsByUid.put(uid, mailboxItem);
            mailboxMessageList.add(mailboxItem);
            log.trace("## handleMailboxItem uid={}\nhash={}",
                    uid,
                    P2PDataStorage.get32ByteHashAsByteArray(mailboxItem.getProtectedMailboxStorageEntry().getProtectedStoragePayload()));

            requestPersistence();
        }

        // In case we had the item already stored we still prefer to apply it again to the domain.
        // Clients need to deal with the case that they get called multiple times with the same mailbox message.
        // This happens also because peer republish certain trade messages for higher resilience. Those messages
        // will be different mailbox messages instances but have the same internal content.
        if (mailboxItem.isMine()) {
            processMyMailboxItem(mailboxItem, uid);
        }
    }

    private void processMyMailboxItem(MailboxItem mailboxItem, String uid) {
        DecryptedMessageWithPubKey decryptedMessageWithPubKey = checkNotNull(mailboxItem.getDecryptedMessageWithPubKey());
        MailboxMessage mailboxMessage = (MailboxMessage) decryptedMessageWithPubKey.getNetworkEnvelope();
        NodeAddress sender = mailboxMessage.getSenderNodeAddress();
        log.info("Received a {} mailbox message with uid {} and senderAddress {}",
                mailboxMessage.getClass().getSimpleName(), uid, sender);
        decryptedMailboxListeners.forEach(e -> e.onMailboxMessageAdded(decryptedMessageWithPubKey, sender));

        if (isBootstrapped) {
            // After we notified our listeners we remove the data immediately from the network.
            // In case the client has not been ready it need to take it via getMailBoxMessages.
            // We do not remove the data from our local map at that moment. This has to be called explicitely from the
            // client after processing. In case processing fails for some reason we still have the local data which can
            // be applied after restart, but the network got cleaned from pending mailbox messages.
            removeMailboxEntryFromNetwork(mailboxItem.getProtectedMailboxStorageEntry());
        } else {
            log.info("We are not bootstrapped yet, so we remove later once the mailBoxMessage got processed.");
        }
    }

    private void addMailboxData(MailboxStoragePayload expirableMailboxStoragePayload,
                                PublicKey receiversPublicKey,
                                SendMailboxMessageListener sendMailboxMessageListener) {
        if (!isBootstrapped) {
            throw new NetworkNotReadyException();
        }

        if (networkNode.getAllConnections().isEmpty()) {
            sendMailboxMessageListener.onFault("There are no P2P network nodes connected. " +
                    "Please check your internet connection.");
            return;
        }

        try {
            ProtectedMailboxStorageEntry protectedMailboxStorageEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                    expirableMailboxStoragePayload,
                    keyRing.getSignatureKeyPair(),
                    receiversPublicKey);

            BroadcastHandler.Listener listener = new BroadcastHandler.Listener() {
                @Override
                public void onSufficientlyBroadcast(List<Broadcaster.BroadcastRequest> broadcastRequests) {
                    broadcastRequests.stream()
                            .filter(broadcastRequest -> broadcastRequest.getMessage() instanceof AddDataMessage)
                            .filter(broadcastRequest -> {
                                AddDataMessage addDataMessage = (AddDataMessage) broadcastRequest.getMessage();
                                return addDataMessage.getProtectedStorageEntry().equals(protectedMailboxStorageEntry);
                            })
                            .forEach(e -> sendMailboxMessageListener.onStoredInMailbox());
                }

                @Override
                public void onNotSufficientlyBroadcast(int numOfCompletedBroadcasts, int numOfFailedBroadcast) {
                    sendMailboxMessageListener.onFault("Message was not sufficiently broadcast.\n" +
                            "numOfCompletedBroadcasts: " + numOfCompletedBroadcasts + ".\n" +
                            "numOfFailedBroadcast=" + numOfFailedBroadcast);
                }
            };
            boolean result = p2PDataStorage.addProtectedStorageEntry(protectedMailboxStorageEntry, networkNode.getNodeAddress(), listener);
            if (!result) {
                sendMailboxMessageListener.onFault("Data already exists in our local database");

                // This should only fail if there are concurrent calls to addProtectedStorageEntry with the
                // same ProtectedMailboxStorageEntry. This is an unexpected use case so if it happens we
                // want to see it, but it is not worth throwing an exception.
                log.error("Unexpected state: adding mailbox message that already exists.");
            }
        } catch (CryptoException e) {
            log.error("Signing at getMailboxDataWithSignedSeqNr failed.");
        }
    }

    private void removeMailboxEntryFromNetwork(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        MailboxStoragePayload mailboxStoragePayload = (MailboxStoragePayload) protectedMailboxStorageEntry.getProtectedStoragePayload();
        PublicKey receiversPubKey = protectedMailboxStorageEntry.getReceiversPubKey();
        try {
            ProtectedMailboxStorageEntry updatedEntry = p2PDataStorage.getMailboxDataWithSignedSeqNr(
                    mailboxStoragePayload,
                    keyRing.getSignatureKeyPair(),
                    receiversPubKey);

            P2PDataStorage.ByteArray hashOfPayload = P2PDataStorage.get32ByteHashAsByteArray(mailboxStoragePayload);
            if (p2PDataStorage.getMap().containsKey(hashOfPayload)) {
                boolean result = p2PDataStorage.remove(updatedEntry, networkNode.getNodeAddress());
                if (result) {
                    log.info("Removed mailboxEntry from network");
                } else {
                    log.warn("Removing mailboxEntry from network failed");
                }
            } else {
                log.info("The mailboxEntry was already removed earlier.");
            }
        } catch (CryptoException e) {
            e.printStackTrace();
            log.error("Could not remove ProtectedMailboxStorageEntry from network. Error: {}", e.toString());
        }
    }

    private void maybeRepublishMailBoxMessages() {
        // We only do the republishing if option is set (default is false) to avoid that the network gets too much traffic.
        // 1000 mailbox messages are about 3 MB, so that would cause quite some load if all nodes would do that.
        // We enable it on one v2 and one v3 seed node so we gain some resilience without causing much load. In
        // emergency case we can enable it on demand at any node.
        if (!republishMailboxEntries) {
            return;
        }
        log.info("We will republish our persisted mailbox messages after a delay of {} sec.", REPUBLISH_DELAY_SEC);

        log.trace("## republishMailBoxMessages mailboxItemsByUid={}", mailboxItemsByUid.keySet());
        UserThread.runAfter(() -> {
            // In addProtectedStorageEntry we break early if we have already received a remove message for that entry.
            republishInChunks(mailboxItemsByUid.values().stream()
                    .filter(e -> !e.isExpired(clock))
                    .map(MailboxItem::getProtectedMailboxStorageEntry)
                    .collect(Collectors.toCollection(ArrayDeque::new)));
        }, REPUBLISH_DELAY_SEC);
    }

    // We republish buckets of 50 items which is about 200 kb. With 20 connections at a seed node that results in
    // 4 MB in total. For 1000 messages it takes 40 min with a 2 min delay. We do that republishing just for
    // additional resilience and as a backup in case all seed nodes would fail to prevent that mailbox messages would
    // get lost. A long delay for republishing is preferred over too much network load.
    private void republishInChunks(Queue<ProtectedMailboxStorageEntry> queue) {
        int chunkSize = 50;
        log.info("Republish a bucket of {} persisted mailbox messages out of {}.", chunkSize, queue.size());
        int i = 0;
        while (!queue.isEmpty() && i < chunkSize) {
            ProtectedMailboxStorageEntry protectedMailboxStorageEntry = queue.poll();
            i++;
            // Broadcaster will accumulate messages in a BundleOfEnvelopes
            p2PDataStorage.republishExistingProtectedMailboxStorageEntry(protectedMailboxStorageEntry,
                    networkNode.getNodeAddress(),
                    null);
        }
        if (!queue.isEmpty()) {
            // We delay 2 minutes to not overload the network
            UserThread.runAfter(() -> republishInChunks(queue), REPUBLISH_DELAY_SEC);
        }
    }

    private void removeMailboxItemFromLocalStore(String uid) {
        MailboxItem mailboxItem = mailboxItemsByUid.get(uid);
        mailboxItemsByUid.remove(uid);
        mailboxMessageList.remove(mailboxItem);
        log.trace("## removeMailboxItemFromMap uid={}\nhash={}\nmailboxItemsByUid={}",
                uid,
                P2PDataStorage.get32ByteHashAsByteArray(mailboxItem.getProtectedMailboxStorageEntry().getProtectedStoragePayload()),
                mailboxItemsByUid.keySet()
        );
        requestPersistence();
    }

    private void requestPersistence() {
        persistenceManager.requestPersistence();
    }
}
