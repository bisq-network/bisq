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
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.app.misc;

import bisq.core.app.BisqExecutable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ExecutableForAppWithP2pTest {
    @Test
    void repeatedRequestDoesNotRepeatSubclassShutdownWork() {
        TestExecutable executable = new TestExecutable();
        AtomicInteger firstCompletionCount = new AtomicInteger();
        AtomicInteger repeatedCompletionCount = new AtomicInteger();

        executable.gracefulShutDown(firstCompletionCount::incrementAndGet);
        executable.gracefulShutDown(repeatedCompletionCount::incrementAndGet);

        assertEquals(1, executable.additionalShutDownCalls);
        assertEquals(BisqExecutable.EXIT_SUCCESS, executable.exitStatus);
        assertEquals(1, firstCompletionCount.get());
        assertEquals(1, repeatedCompletionCount.get());
    }

    @Test
    void laterFailureRequestDoesNotReplaceNormalExitStatus() {
        TestExecutable executable = new TestExecutable();

        executable.gracefulShutDown(() -> {
        });
        executable.requestFailureShutDown();

        assertEquals(BisqExecutable.EXIT_SUCCESS, executable.exitStatus);
        assertEquals(1, executable.additionalShutDownCalls);
    }

    @Test
    void failureRequestRetainsFailureExitStatus() {
        TestExecutable executable = new TestExecutable();

        executable.requestFailureShutDown();

        assertEquals(BisqExecutable.EXIT_FAILURE, executable.exitStatus);
        assertEquals(1, executable.additionalShutDownCalls);
    }

    @Test
    void laterNormalRequestDoesNotReplaceFailureExitStatus() {
        TestExecutable executable = new TestExecutable();
        AtomicInteger completionCount = new AtomicInteger();

        executable.requestFailureShutDown();
        executable.gracefulShutDown(completionCount::incrementAndGet);

        assertEquals(BisqExecutable.EXIT_FAILURE, executable.exitStatus);
        assertEquals(1, executable.additionalShutDownCalls);
        assertEquals(1, completionCount.get());
    }

    @Test
    void failingSubclassShutDownStillCompletesWithFailureExitStatus() {
        TestExecutable executable = new TestExecutable();
        executable.failAdditionalShutDown = true;
        AtomicInteger completionCount = new AtomicInteger();

        executable.gracefulShutDown(completionCount::incrementAndGet);

        assertEquals(BisqExecutable.EXIT_FAILURE, executable.exitStatus);
        assertEquals(1, executable.additionalShutDownCalls);
        assertEquals(1, completionCount.get());
    }

    private static final class TestExecutable extends ExecutableForAppWithP2p {
        private int additionalShutDownCalls;
        private Integer exitStatus;
        private boolean failAdditionalShutDown;

        private TestExecutable() {
            super("test", "test", "test", "test");
        }

        @Override
        protected void shutDownAdditionalServices() {
            additionalShutDownCalls++;
            if (failAdditionalShutDown) {
                throw new IllegalStateException("test");
            }
        }

        // We must not call the base implementation as it would terminate the JVM. We keep the notification of
        // the completion handlers, which the base implementation does before it schedules the exit.
        @Override
        protected void completeShutDown(int status, long delay, TimeUnit timeUnit) {
            assertNull(exitStatus);
            exitStatus = status;
            notifyGracefulShutDownComplete();
        }

        private void requestFailureShutDown() {
            shutDown();
        }
    }
}
