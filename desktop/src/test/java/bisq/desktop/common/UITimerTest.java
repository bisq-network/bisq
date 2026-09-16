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

package bisq.desktop.common;

import bisq.common.UserThread;
import bisq.common.reactfx.FxTimer;

import javafx.application.Platform;

import java.time.Duration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

class UITimerTest {
    @BeforeEach
    void setUp() {
        UserThread.resetForTests();
        UserThread.setExecutor(Runnable::run);
    }

    @AfterEach
    void tearDown() {
        UserThread.resetForTests();
    }

    @Test
    void timerCreatedBeforeJvmShutdownDoesNotRunAfterTheHandover() {
        var action = new AtomicReference<Runnable>();
        var executions = new AtomicInteger();
        var fxTimer = mock(bisq.common.reactfx.Timer.class);
        Duration delay = Duration.ofSeconds(1);

        try (MockedStatic<Platform> mockedPlatform = mockStatic(Platform.class);
             MockedStatic<FxTimer> mockedFxTimer = mockStatic(FxTimer.class)) {
            mockedPlatform.when(Platform::isFxApplicationThread).thenReturn(false);
            mockedFxTimer.when(() -> FxTimer.create(eq(delay), any(Runnable.class)))
                    .thenAnswer(invocation -> {
                        action.set(invocation.getArgument(1));
                        return fxTimer;
                    });

            UITimer timer = new UITimer();
            timer.runLater(delay, executions::incrementAndGet);
            assertNotNull(action.get());
            verify(fxTimer).restart();

            UserThread.executeAtShutdown(() -> {
            });
            action.get().run();

            assertEquals(0, executions.get());
        }
    }
}
