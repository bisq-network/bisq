package bisq.core.app;

import lombok.extern.slf4j.Slf4j;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class ShutdownDelayerTests {

    @Slf4j
    static class FakeClock implements ShutdownDelayer.Clock {
        private long timeInMillis = System.currentTimeMillis();

        @Override
        public long getTimeInMillis() {
            return timeInMillis;
        }

        @Override
        public void waitForMillis(int millis) {
            timeInMillis += millis;
        }
    }

    @Test
    void noDelayTest() {
        ShutdownDelayer.Clock clock = new FakeClock();
        ShutdownDelayer.setClock(clock);

        long startTime = clock.getTimeInMillis();
        ShutdownDelayer.maybeWaitForPendingMessagesToBePublished();
        long endTime = clock.getTimeInMillis();

        long diff = endTime - startTime;
        assertEquals(0, diff, "There should be no shutdown delay because we have no pending messages.");
    }

    @Test
    void delayTest() {
        ShutdownDelayer.Clock clock = new FakeClock();
        ShutdownDelayer.setClock(clock);

        ShutdownDelayer.reportPendingPaymentReceivedMessage();
        ShutdownDelayer.reportPendingPaymentReceivedMessage();

        long startTime = clock.getTimeInMillis();
        ShutdownDelayer.maybeWaitForPendingMessagesToBePublished();
        long endTime = clock.getTimeInMillis();

        // If we don't clean up the pending messages the other test will start in a
        // pending state.
        ShutdownDelayer.reportPaymentReceivedMessageSent();
        ShutdownDelayer.reportPaymentReceivedMessageSent();

        long diff = endTime - startTime;
        if (diff < ShutdownDelayer.SHUTDOWN_TIMEOUT) {
            fail("Timeout is shorter than " + ShutdownDelayer.SHUTDOWN_TIMEOUT);
        }
    }
}
