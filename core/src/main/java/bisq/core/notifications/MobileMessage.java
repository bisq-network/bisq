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

package bisq.core.notifications;

import bisq.common.util.JsonExclude;

import java.util.Date;

import lombok.Value;

@Value
public class MobileMessage {
    private long sentDate;
    private String txId;
    private String title;
    private String message;
    @JsonExclude
    transient private MobileMessageType mobileMessageType;
    private String type;
    private String actionRequired;
    private int version;

    public MobileMessage(String title, String message, MobileMessageType mobileMessageType) {
        this(title, message, "", mobileMessageType);
    }

    public MobileMessage(String title, String message, String txId, MobileMessageType mobileMessageType) {
        this.title = title;
        this.message = message;
        this.txId = txId;
        this.mobileMessageType = mobileMessageType;

        this.type = mobileMessageType.name();
        actionRequired = "";
        sentDate = new Date().getTime();
        version = 1;
    }
}
