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

import java.nio.file.Path;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import static java.nio.charset.StandardCharsets.UTF_8;
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

    // Issue 8160: another process must not get the lock while the owner still flushes its data or exits.
    // The owner runs in its own JVM: a probe in the owner's JVM only sees the JVM's own lock table, and on
    // Linux closing the probe's channel can release the owner's lock in the operating system.
    @Test
    @Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void anotherProcessGetsTheInstanceLockOnlyAfterTheOwnerHasExited(@TempDir Path appDataDir) throws Exception {
        String javaCommand = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process owner = new ProcessBuilder(javaCommand, "-cp", System.getProperty("java.class.path"),
                InstanceLockOwner.class.getName(), appDataDir.toString())
                .redirectErrorStream(true)
                .start();
        try (var ownerOutput = new BufferedReader(new InputStreamReader(owner.getInputStream(), UTF_8));
             var ownerInput = new PrintWriter(owner.getOutputStream(), true, UTF_8)) {
            awaitLine(ownerOutput, InstanceLockOwner.FLUSH_PENDING);
            assertFalse(canAcquireInstanceLock(appDataDir), "The lock must be held while the flush is pending");

            ownerInput.println();
            awaitLine(ownerOutput, InstanceLockOwner.EXITING);
            assertFalse(canAcquireInstanceLock(appDataDir), "The lock must be held until the process has exited");

            ownerInput.println();
            assertEquals(BisqExecutable.EXIT_SUCCESS, owner.waitFor());
            assertTrue(canAcquireInstanceLock(appDataDir), "The lock must be free after the process has exited");
        } finally {
            owner.destroyForcibly();
        }
    }

    private static void awaitLine(BufferedReader reader, String expected) throws IOException {
        var output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.equals(expected)) {
                return;
            }
            output.append(line).append(System.lineSeparator());
        }
        throw new AssertionError("The lock owner ended before printing '" + expected + "':" +
                System.lineSeparator() + output);
    }

    private static boolean canAcquireInstanceLock(Path appDataDir) throws IOException {
        try (var probe = new InstanceLock(appDataDir)) {
            return probe.tryLock();
        }
    }

    private static final class ConfigErrorHandled extends RuntimeException {
    }

    private static class TestBisqExecutable extends BisqExecutable {
        private String configErrorMessage;

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
    }

    // Takes the lock, then shuts down through flushAndExit() with the real exit. Like the production flush,
    // the flush completes after flushAndExit() has returned. It waits for a line on stdin while the flush is
    // pending and again in a shutdown hook, so the test can probe the lock at both points.
    public static final class InstanceLockOwner extends TestBisqExecutable {
        static final String FLUSH_PENDING = "flush pending";
        static final String EXITING = "exiting";

        private ResultHandler pendingFlushHandler;

        public static void main(String[] args) throws IOException {
            var input = new BufferedReader(new InputStreamReader(System.in, UTF_8));
            var owner = new InstanceLockOwner();
            owner.instanceLock = new InstanceLock(Path.of(args[0]));
            if (!owner.instanceLock.tryLock()) {
                System.exit(EXIT_FAILURE);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println(EXITING);
                System.out.flush();
                try {
                    input.readLine();
                } catch (IOException ignored) {
                }
            }));

            owner.flushAndExit(EXIT_SUCCESS);
            System.out.println(FLUSH_PENDING);
            System.out.flush();
            input.readLine();
            owner.pendingFlushHandler.handleResult();
        }

        @Override
        protected void flushPersistableData(ResultHandler completeHandler) {
            pendingFlushHandler = completeHandler;
        }
    }
}
