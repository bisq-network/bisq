package io.bitsquare.gui.main.intructions;

import com.google.inject.Inject;
import io.bitsquare.gui.main.notifications.Notification;
import io.bitsquare.trade.TradeManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

public class InstructionCenter {
    private final Logger log = LoggerFactory.getLogger(InstructionCenter.class);
    private Queue<io.bitsquare.gui.main.notifications.Notification> notifications = new LinkedBlockingQueue<>(3);
    private io.bitsquare.gui.main.notifications.Notification displayedNotification;
    private TradeManager tradeManager;

    @Inject
    public InstructionCenter(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    void queueForDisplay(io.bitsquare.gui.main.notifications.Notification notification) {
        boolean result = notifications.offer(notification);
        if (!result)
            log.warn("The capacity is full with popups in the queue.\n\t" +
                    "Not added new notification=" + notification);
        displayNext();
    }

    void isHidden(Notification notification) {
        if (displayedNotification == null || displayedNotification == notification) {
            displayedNotification = null;
            displayNext();
        } else {
            log.warn("We got a isHidden called with a wrong notification.\n\t" +
                    "notification (argument)=" + notification + "\n\tdisplayedPopup=" + displayedNotification);
        }
    }

    private void displayNext() {
        if (displayedNotification == null) {
            if (!notifications.isEmpty()) {
                displayedNotification = notifications.poll();
                displayedNotification.display();
            }
        }
    }
}
