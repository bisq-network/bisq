package io.bitsquare.gui.main.notifications;

import io.bitsquare.gui.main.popups.Popup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Notification extends Popup {
    private static final Logger log = LoggerFactory.getLogger(Notification.class);
    private boolean hasBeenDisplayed;

    public Notification() {
        NotificationCenter.add(this);
    }

    public Notification headLine(String headLine) {
        return (Notification) super.headLine(headLine);
    }

    public Notification tradeHeadLine(String tradeId) {
        return headLine("Notification for trade with ID " + tradeId);
    }

    public Notification disputeHeadLine(String tradeId) {
        return headLine("Support ticket for trade with ID " + tradeId);
    }

    public Notification message(String message) {
        return (Notification) super.message(message);
    }

    public void show() {
        super.show();
        hasBeenDisplayed = true;
    }

    public void hide() {
        super.hide();
    }

    public boolean isHasBeenDisplayed() {
        return hasBeenDisplayed;
    }
}
