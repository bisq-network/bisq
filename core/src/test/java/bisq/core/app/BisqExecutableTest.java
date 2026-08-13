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

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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

    private static final class TestBisqExecutable extends BisqExecutable {
        private TestBisqExecutable() {
            super("test", "test", "test", "test");
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
}
