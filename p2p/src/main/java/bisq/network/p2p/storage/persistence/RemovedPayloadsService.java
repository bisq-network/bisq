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

package bisq.network.p2p.storage.persistence;

import bisq.network.p2p.storage.P2PDataStorage;
import bisq.network.p2p.storage.payload.MailboxStoragePayload;

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;
import javax.inject.Singleton;

import lombok.extern.slf4j.Slf4j;

/**
 * We persist the hashes and timestamp when a AddOncePayload payload got removed. This protects that it could be
 * added again for instance if the sequence number map would be inconsistent/deleted or when we receive data from
 * seed nodes where we do skip some checks.
 */
@Singleton
@Slf4j
public class RemovedPayloadsService implements PersistedDataHost {
    private final PersistenceManager<RemovedPayloadsMap> persistenceManager;
    private final RemovedPayloadsMap removedPayloadsMap = new RemovedPayloadsMap();

    @Inject
    public RemovedPayloadsService(PersistenceManager<RemovedPayloadsMap> persistenceManager) {
        this.persistenceManager = persistenceManager;

        this.persistenceManager.initialize(removedPayloadsMap, PersistenceManager.Source.PRIVATE_LOW_PRIO);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        long cutOffDate = System.currentTimeMillis() - MailboxStoragePayload.TTL;
        persistenceManager.readPersisted(persisted -> {
                    persisted.getDateByHashes().entrySet().stream()
                            .filter(e -> e.getValue() > cutOffDate)
                            .forEach(e -> removedPayloadsMap.getDateByHashes().put(e.getKey(), e.getValue()));
                    log.trace("## readPersisted: removedPayloadsMap size={}", removedPayloadsMap.getDateByHashes().size());
                    persistenceManager.requestPersistence();
                    completeHandler.run();
                },
                completeHandler);
    }

    public boolean wasRemoved(P2PDataStorage.ByteArray hashOfPayload) {
        log.trace("## called wasRemoved: hashOfPayload={}, removedPayloadsMap={}", hashOfPayload.toString(), removedPayloadsMap);
        return removedPayloadsMap.getDateByHashes().containsKey(hashOfPayload);
    }

    public void addHash(P2PDataStorage.ByteArray hashOfPayload) {
        log.trace("## called addHash: hashOfPayload={}, removedPayloadsMap={}", hashOfPayload.toString(), removedPayloadsMap);
        removedPayloadsMap.getDateByHashes().putIfAbsent(hashOfPayload, System.currentTimeMillis());
        persistenceManager.requestPersistence();
    }
}
