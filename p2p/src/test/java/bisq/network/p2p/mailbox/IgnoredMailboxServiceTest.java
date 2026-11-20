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

import bisq.common.persistence.PersistenceManager;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.IOException;
import java.io.OutputStream;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IgnoredMailboxServiceTest {
    @Test
    void readsTheHashCacheFromItsOwnFile(@TempDir Path directory) throws IOException {
        Path oldFile = directory.resolve("IgnoredMailboxMap");
        writeCache(oldFile, "old-message-id");

        PersistenceProtoResolver resolver = mock(PersistenceProtoResolver.class);
        when(resolver.fromProto(any(protobuf.PersistableEnvelope.class))).thenAnswer(invocation -> {
            protobuf.PersistableEnvelope envelope = invocation.getArgument(0);
            return IgnoredMailboxMap.fromProto(envelope.getIgnoredMailboxMap());
        });
        PersistenceManager<IgnoredMailboxMap> persistenceManager = new PersistenceManager<>(directory.toFile(), resolver, null);
        new IgnoredMailboxService(persistenceManager);
        try {
            assertNull(persistenceManager.getPersisted());
            String hash = "ab".repeat(32);
            writeCache(directory.resolve("IgnoredMailboxPayloadHashes"), hash);
            assertEquals(Set.of(hash), persistenceManager.getPersisted().getDataMap().keySet());
            assertTrue(Files.exists(oldFile));
        } finally {
            persistenceManager.shutdown();
        }
    }

    private void writeCache(Path file, String key) throws IOException {
        IgnoredMailboxMap cache = new IgnoredMailboxMap();
        cache.put(key, System.currentTimeMillis());
        try (OutputStream output = Files.newOutputStream(file)) {
            cache.toProtoMessage().writeDelimitedTo(output);
        }
    }
}
