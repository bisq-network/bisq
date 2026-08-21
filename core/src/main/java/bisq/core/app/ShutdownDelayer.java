package bisq.core.app;

import java.time.Duration;

import java.util.concurrent.atomic.AtomicInteger;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ShutdownDelayer {

    interface Clock {
        long getTimeInMillis();

        void waitForMillis(int millis) throws InterruptedException;
    }

    @Slf4j
    public static class BlockingClock implements Clock {
        @Override
        public long getTimeInMillis() {
            return System.currentTimeMillis();
        }

        @Override
        public void waitForMillis(int millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }

    public static final long SHUTDOWN_TIMEOUT = Duration.ofSeconds(2).toMillis();
    private static final AtomicInteger pendingPaymentReceivedMessages = new AtomicInteger(0);

    @Setter
    private static Clock clock;

    public static void reportPendingPaymentReceivedMessage() {
        pendingPaymentReceivedMessages.incrementAndGet();
    }

    public static void reportPaymentReceivedMessageSent() {
        pendingPaymentReceivedMessages.decrementAndGet();
    }

    public static void maybeWaitForPendingMessagesToBePublished() {
        int numberOfPendingMessages = pendingPaymentReceivedMessages.get();
        if (numberOfPendingMessages > 0) {
            log.info("Payment received messages are pending. Waiting for 2 more seconds");
            waitForMessagesToBeSent(numberOfPendingMessages);
        }
    }

    private static void waitForMessagesToBeSent(int numberOfPendingMessages) {
        long deadline = clock.getTimeInMillis() + SHUTDOWN_TIMEOUT;
        while (numberOfPendingMessages > 0 && clock.getTimeInMillis() <= deadline) {
            try {
                clock.waitForMillis(200);
            } catch (InterruptedException e) {
                // Nothing to do.
            } finally {
                numberOfPendingMessages = pendingPaymentReceivedMessages.get();
            }
        }

        if (numberOfPendingMessages > 0 && clock.getTimeInMillis() >= deadline) {
            log.info("2 second timer expired.");
        }
    }
}
