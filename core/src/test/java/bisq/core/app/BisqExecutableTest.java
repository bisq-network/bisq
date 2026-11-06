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

package bisq.core.app;

import bisq.common.app.AppModule;
import bisq.common.app.InstanceLock;
import bisq.common.handlers.ResultHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BisqExecutableTest {

    @Test
    void repeatedShutdownRequestJoinsInProgressShutdown() {
        var executable = new TestBisqExecutable();
        var firstCompletionCount = new AtomicInteger();
        var repeatedCompletionCount = new AtomicInteger();

        assertTrue(executable.beginGracefulShutDown(firstCompletionCount::incrementAndGet));
        assertFalse(executable.beginGracefulShutDown(repeatedCompletionCount::incrementAndGet));
        assertEquals(0, firstCompletionCount.get());
        assertEquals(0, repeatedCompletionCount.get());

        executable.notifyGracefulShutDownComplete();

        assertEquals(1, firstCompletionCount.get());
        assertEquals(1, repeatedCompletionCount.get());
    }

    @Test
    void shutdownRequestAfterCompletionIsNotifiedImmediately() {
        var executable = new TestBisqExecutable();
        var completionCount = new AtomicInteger();

        assertTrue(executable.beginGracefulShutDown(() -> {
        }));
        executable.notifyGracefulShutDownComplete();

        assertFalse(executable.beginGracefulShutDown(completionCount::incrementAndGet));
        assertEquals(1, completionCount.get());
    }

    @Test
    void failingCompletionHandlerDoesNotPreventOtherNotifications() {
        var executable = new TestBisqExecutable();
        var completionCount = new AtomicInteger();

        assertTrue(executable.beginGracefulShutDown(() -> {
            throw new IllegalStateException("test");
        }));
        assertFalse(executable.beginGracefulShutDown(completionCount::incrementAndGet));

        executable.notifyGracefulShutDownComplete();

        assertEquals(1, completionCount.get());
    }

    @Test
    void anOptionWhichIsNotSupportedAnymoreReachesTheConfigErrorHandler(@TempDir Path appDataDir) {
        // The desktop application overrides the handler to show the reason in a dialog
        var executable = new TestBisqExecutable();

        assertThrows(ConfigErrorHandled.class, () -> executable.execute(new String[]{
                "--appDataDir=" + appDataDir, "--torControlPort=9051"}));

        assertTrue(executable.configErrorMessage.contains("option 'torControlPort' is not supported anymore"));
    }

    @Test
    void instanceLockIsHeldUntilTheFlushHasCompleted(@TempDir Path appDataDir) throws IOException {
        var executable = new TestBisqExecutable();
        executable.instanceLock = new InstanceLock(appDataDir);
        assertTrue(executable.instanceLock.tryLock());
        executable.lockProbeDir = appDataDir;

        executable.flushAndExit(BisqExecutable.EXIT_SUCCESS);

        assertTrue(executable.lockHeldDuringFlush, "The lock must be held while the data is flushed");
        assertTrue(executable.lockFreeOnCompletion, "The lock must be released when the shutdown completes");
    }

    private static final class ConfigErrorHandled extends RuntimeException {
    }

    private static final class TestBisqExecutable extends BisqExecutable {
        private String configErrorMessage;
        private Path lockProbeDir;
        private boolean lockHeldDuringFlush;
        private boolean lockFreeOnCompletion;

        private TestBisqExecutable() {
            super("test", "test", "test", "test");
        }

        // The real handler exits, so the start must not go on here either
        @Override
        protected void handleConfigError(String message) {
            configErrorMessage = message;
            throw new ConfigErrorHandled();
        }

        @Override
        protected void configUserThread() {
        }

        @Override
        protected void launchApplication() {
        }

        @Override
        protected AppModule getModule() {
            return null;
        }

        @Override
        protected void startApplication() {
        }

        @Override
        public void onSetupComplete() {
        }

        // The real flush and the real exit are not part of these tests
        @Override
        protected void flushPersistableData(ResultHandler completeHandler) {
            if (lockProbeDir == null) {
                super.flushPersistableData(completeHandler);
                return;
            }
            lockHeldDuringFlush = isLockedByAnotherInstance(lockProbeDir);
            completeHandler.handleResult();
        }

        @Override
        protected void completeShutDown(int status) {
            if (lockProbeDir == null) {
                super.completeShutDown(status);
                return;
            }
            lockFreeOnCompletion = !isLockedByAnotherInstance(lockProbeDir);
        }

        private static boolean isLockedByAnotherInstance(Path appDataDir) {
            try (var probe = new InstanceLock(appDataDir)) {
                return !probe.tryLock();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
