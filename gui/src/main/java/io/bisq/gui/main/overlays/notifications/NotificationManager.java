package io.bisq.gui.main.overlays.notifications;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class NotificationManager {
    private static final Logger log = LoggerFactory.getLogger(NotificationManager.class);
    private static final Queue<Notification> popups = new LinkedBlockingQueue<>(5);
    private static Notification displayedPopup;

    public static void queueForDisplay(Notification popup) {
        boolean result = popups.offer(popup);
        if (!result)
            log.warn("The capacity is full with popups in the queue.\n\t" +
                    "Not added new popup=" + popup);
        displayNext();
    }

    public static void onHidden(Notification popup) {
        if (displayedPopup == null || displayedPopup == popup) {
            displayedPopup = null;
            displayNext();
        } else {
            log.warn("We got a isHidden called with a wrong popup.\n\t" +
                    "popup (argument)=" + popup + "\n\tdisplayedPopup=" + displayedPopup);
        }
    }

    private static void displayNext() {
        if (displayedPopup == null) {
            if (!popups.isEmpty()) {
                displayedPopup = popups.poll();
                displayedPopup.onReadyForDisplay();
            }
        }
    }
}
