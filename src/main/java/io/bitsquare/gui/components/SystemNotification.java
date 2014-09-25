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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import eu.hansolo.enzo.notification.Notification;
import eu.hansolo.enzo.notification.NotificationBuilder;
import eu.hansolo.enzo.notification.NotifierBuilder;

/**
 * Not sure if we stick with the eu.hansolo.enzo.notification.Notification implementation, so keep it behind a facade
 */
public class SystemNotification {
    private static final Logger log = LoggerFactory.getLogger(SystemNotification.class);
    private static final Notification.Notifier notifier = NotifierBuilder.create().build();

    public static void openInfoNotification(String headline, String message) {
        notifier.notify(NotificationBuilder.create().title(headline).message(message).build());
    }

}
