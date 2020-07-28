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

package bisq.desktop.main.overlays.popups;

import bisq.common.UserThread;

import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PopupManager {
    private static final Logger log = LoggerFactory.getLogger(PopupManager.class);
    private static final Queue<Popup> popups = new LinkedBlockingQueue<>(5);

    private static Popup displayedPopup;

    public static void queueForDisplay(Popup popup) {
        boolean result = popups.offer(popup);
        if (!result)
            log.warn("The capacity is full with popups in the queue.\n\t" +
                    "Not added new popup=" + popup);
        displayNext();
    }

    public static void onHidden(Popup popup) {
        if (displayedPopup == null || displayedPopup == popup) {
            displayedPopup = null;
            UserThread.runAfter(() -> { displayNext(); }, 100, TimeUnit.MILLISECONDS);
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
