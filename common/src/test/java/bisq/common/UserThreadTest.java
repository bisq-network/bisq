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

package bisq.common;

import java.time.Duration;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserThreadTest {

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    @Test
    void externalShutdownReplacesFrameworkExecutorAndTimer() {
        var frameworkExecutions = new AtomicInteger();
        Executor frameworkExecutor = command -> frameworkExecutions.incrementAndGet();
        var shutdownExecutions = new AtomicInteger();
        Executor shutdownExecutor = command -> {
            shutdownExecutions.incrementAndGet();
            command.run();
        };
        var commandExecutions = new AtomicInteger();

        UserThread.setExecutor(frameworkExecutor);
        UserThread.setTimerClass(FrameworkTimer.class);
        UserThread.setShutdownExecutor(shutdownExecutor, ShutdownTimer.class);

        UserThread.executeAtShutdown(commandExecutions::incrementAndGet);
        UserThread.execute(commandExecutions::incrementAndGet);

        assertTrue(UserThread.isJvmShutdownInProgress());
        assertEquals(0, frameworkExecutions.get());
        assertEquals(2, shutdownExecutions.get());
        assertEquals(2, commandExecutions.get());
        assertInstanceOf(ShutdownTimer.class, UserThread.runAfter(() -> {
        }, 1));
    }

    @Test
    void explicitShutdownExecutorIsNotOverwrittenByLaterExecutorConfiguration() {
        var shutdownExecutions = new AtomicInteger();
        Executor shutdownExecutor = command -> {
            shutdownExecutions.incrementAndGet();
            command.run();
        };
        var frameworkExecutions = new AtomicInteger();
        Executor frameworkExecutor = command -> frameworkExecutions.incrementAndGet();

        UserThread.setShutdownExecutor(shutdownExecutor, ShutdownTimer.class);
        UserThread.setExecutor(frameworkExecutor);
        UserThread.setTimerClass(FrameworkTimer.class);

        UserThread.executeAtShutdown(() -> {
        });

        assertEquals(0, frameworkExecutions.get());
        assertEquals(1, shutdownExecutions.get());
        assertInstanceOf(ShutdownTimer.class, UserThread.runAfter(() -> {
        }, 1));
    }

    static class FrameworkTimer implements Timer {
        @Override
        public Timer runLater(Duration delay, Runnable runnable) {
            return this;
        }

        @Override
        public Timer runPeriodically(Duration interval, Runnable runnable) {
            return this;
        }

        @Override
        public void stop() {
        }
    }

    static final class ShutdownTimer extends FrameworkTimer {
    }
}
