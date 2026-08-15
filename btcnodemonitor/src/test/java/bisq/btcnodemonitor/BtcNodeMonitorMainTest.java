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

package bisq.btcnodemonitor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BtcNodeMonitorMainTest {
    @Test
    void repeatedRequestJoinsOneServiceShutdown() {
        BtcNodeMonitor btcNodeMonitor = mock(BtcNodeMonitor.class);
        when(btcNodeMonitor.shutdown()).thenReturn(CompletableFuture.completedFuture(null));
        TestBtcNodeMonitorMain main = new TestBtcNodeMonitorMain(btcNodeMonitor);
        AtomicInteger completionCount = new AtomicInteger();

        main.gracefulShutDown(completionCount::incrementAndGet);
        main.gracefulShutDown(completionCount::incrementAndGet);

        verify(btcNodeMonitor, times(1)).shutdown();
        assertEquals(2, completionCount.get());
        // The repeated exit request is dropped by the guard in CommonSetup.exitAfter, which the test replaces.
        assertEquals(2, main.exitCalls);
    }

    @Test
    void failedServiceShutdownStillCompletes() {
        BtcNodeMonitor btcNodeMonitor = mock(BtcNodeMonitor.class);
        when(btcNodeMonitor.shutdown()).thenReturn(CompletableFuture.failedFuture(new RuntimeException("test")));
        TestBtcNodeMonitorMain main = new TestBtcNodeMonitorMain(btcNodeMonitor);
        AtomicInteger completionCount = new AtomicInteger();

        main.gracefulShutDown(completionCount::incrementAndGet);

        assertEquals(1, completionCount.get());
        assertEquals(1, main.exitCalls);
    }

    @Test
    void shutDownBeforeServiceIsCreatedCompletes() {
        TestBtcNodeMonitorMain main = new TestBtcNodeMonitorMain(null);
        AtomicInteger completionCount = new AtomicInteger();

        main.gracefulShutDown(completionCount::incrementAndGet);

        assertEquals(1, completionCount.get());
        assertEquals(1, main.exitCalls);
    }

    private static final class TestBtcNodeMonitorMain extends BtcNodeMonitorMain {
        private int exitCalls;

        private TestBtcNodeMonitorMain(BtcNodeMonitor btcNodeMonitor) {
            super(btcNodeMonitor);
        }

        @Override
        void exitAfterShutDown() {
            exitCalls++;
        }
    }
}
