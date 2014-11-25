/*
 * This file is part of Bitsquare.
 *
 * Bitsquare is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bitsquare is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bitsquare. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bitsquare.gui.components;

import io.bitsquare.util.Utilities;

import eu.hansolo.enzo.notification.Notification;
import eu.hansolo.enzo.notification.NotificationBuilder;
import eu.hansolo.enzo.notification.NotifierBuilder;

/**
 * Not sure if we stick with the eu.hansolo.enzo.notification.Notification implementation, so keep it behind a service
 */
public class SystemNotification {
    private static final Notification.Notifier notifier = NotifierBuilder.create().build();

    public static void openInfoNotification(String title, String message) {
        // On windows it causes problems with the hidden stage used in the hansolo Notification implementation
        // Lets deactivate it for the moment and fix that with a more native-like or real native solution later.
        // Lets deactivate it for Linux as well, as it is not much tested yet
        if (Utilities.isOSX())
            notifier.notify(NotificationBuilder.create().title(title).message(message).build());
    }
}
