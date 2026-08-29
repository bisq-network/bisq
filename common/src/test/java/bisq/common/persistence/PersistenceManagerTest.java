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

package bisq.common.persistence;

import bisq.common.file.CorruptedStorageFileHandler;
import bisq.common.proto.persistable.PersistableEnvelope;
import bisq.common.proto.persistable.PersistenceProtoResolver;

import com.google.protobuf.Message;

import java.nio.file.Files;
import java.nio.file.Path;

import java.io.File;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * PersistenceManager.allServicesInitialized is static and cannot be reset, so the tests that need the not yet
 * initialized state run first.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PersistenceManagerTest {
    private static final long TIMEOUT_SEC = 10;

    @TempDir
    Path tempDir;

    @Test
    @Order(1)
    void errorHandlerReplacesCompleteHandlerBeforeInitialization() throws Exception {
        PersistenceManager<PersistableEnvelope> manager = manager("before-init");
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.persistNow(() -> completed.set(true), throwable -> {
            failure.set(throwable);
            latch.countDown();
        });

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(completed.get());
        assertInstanceOf(IllegalStateException.class, failure.get());
        manager.shutdown();
    }

    @Test
    @Order(2)
    void completeHandlerWithoutErrorHandlerIsStillCalledBeforeInitialization() throws Exception {
        PersistenceManager<PersistableEnvelope> manager = manager("legacy-before-init");
        CountDownLatch latch = new CountDownLatch(1);

        manager.persistNow(latch::countDown);

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        manager.shutdown();
    }

    @Test
    @Order(3)
    void errorHandlerReceivesSerializationFailure() throws Exception {
        PersistenceManager.onAllServicesInitialized();
        RuntimeException serializationFailure = new RuntimeException("cannot serialize");
        PersistenceManager<PersistableEnvelope> manager = manager("serialization-failure", () -> {
            throw serializationFailure;
        });
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.persistNow(() -> completed.set(true), throwable -> {
            failure.set(throwable);
            latch.countDown();
        });

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(completed.get());
        assertSame(serializationFailure, failure.get());
        manager.shutdown();
    }

    @Test
    @Order(4)
    void errorHandlerReceivesWriteFailureAndCompleteHandlerIsNotCalled() throws Exception {
        PersistenceManager.onAllServicesInitialized();
        String fileName = "write-failure";
        // A non-empty directory at the storage file path makes replacing the storage file fail on every platform
        File storageFile = new File(tempDir.toFile(), fileName);
        assertTrue(storageFile.mkdir());
        assertTrue(new File(storageFile, "blocker").createNewFile());
        PersistenceManager<PersistableEnvelope> manager = manager(fileName);
        AtomicBoolean completed = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.persistNow(() -> completed.set(true), throwable -> {
            failure.set(throwable);
            latch.countDown();
        });

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertFalse(completed.get());
        assertNotNull(failure.get());
        manager.shutdown();
    }

    @Test
    @Order(5)
    void completeHandlerIsCalledAfterSuccessfulWrite() throws Exception {
        PersistenceManager.onAllServicesInitialized();
        String fileName = "success";
        PersistenceManager<PersistableEnvelope> manager = manager(fileName);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        manager.persistNow(latch::countDown, failure::set);

        assertTrue(latch.await(TIMEOUT_SEC, TimeUnit.SECONDS));
        assertNull(failure.get());
        assertTrue(Files.isRegularFile(tempDir.resolve(fileName)));
        manager.shutdown();
    }

    private PersistenceManager<PersistableEnvelope> manager(String fileName) {
        return manager(fileName, protobuf.PersistableEnvelope::getDefaultInstance);
    }

    private PersistenceManager<PersistableEnvelope> manager(String fileName, Supplier<Message> message) {
        PersistenceManager<PersistableEnvelope> manager = new PersistenceManager<>(tempDir.toFile(),
                mock(PersistenceProtoResolver.class),
                new CorruptedStorageFileHandler());
        manager.initialize(new TestEnvelope(message), fileName, PersistenceManager.Source.PRIVATE);
        return manager;
    }

    private static final class TestEnvelope implements PersistableEnvelope {
        private final Supplier<Message> message;

        private TestEnvelope(Supplier<Message> message) {
            this.message = message;
        }

        @Override
        public Message toProtoMessage() {
            return message.get();
        }
    }
}
