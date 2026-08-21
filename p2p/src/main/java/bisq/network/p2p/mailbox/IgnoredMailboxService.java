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

import bisq.network.p2p.storage.payload.MailboxStoragePayload;

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistedDataHost;

import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * We persist failed attempts to decrypt mailbox messages (expected if mailbox message was not addressed to us).
 * This improves performance at processing mailbox messages.
 * On a fast 4 core machine 1000 mailbox messages take about 1.5 second. At second start-up using the persisted data
 * it only takes about 30 ms.
 */
@Singleton
public class IgnoredMailboxService implements PersistedDataHost {
    private final PersistenceManager<IgnoredMailboxMap> persistenceManager;
    private final IgnoredMailboxMap ignoredMailboxMap = new IgnoredMailboxMap();

    @Inject
    public IgnoredMailboxService(PersistenceManager<IgnoredMailboxMap> persistenceManager) {
        this.persistenceManager = persistenceManager;
        this.persistenceManager.initialize(ignoredMailboxMap, PersistenceManager.Source.PRIVATE_LOW_PRIO);
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PersistedDataHost
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public void readPersisted(Runnable completeHandler) {
        persistenceManager.readPersisted(persisted -> {
                    // At each load we cleanup outdated entries
                    long expiredDate = System.currentTimeMillis() - MailboxStoragePayload.TTL;
                    persisted.getDataMap().entrySet().stream()
                            .filter(e -> e.getValue() > expiredDate)
                            .forEach(e -> ignoredMailboxMap.put(e.getKey(), e.getValue()));
                    persistenceManager.requestPersistence();
                    completeHandler.run();
                },
                completeHandler);
    }

    public boolean isIgnored(String uid) {
        return ignoredMailboxMap.containsKey(uid);
    }

    public void ignore(String uid, long creationTimeStamp) {
        ignoredMailboxMap.put(uid, creationTimeStamp);
        persistenceManager.requestPersistence();
    }
}
