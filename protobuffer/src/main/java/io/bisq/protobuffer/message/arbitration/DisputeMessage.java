/*
 * This file is part of bisq.
 *
 * bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package io.bisq.protobuffer.message.arbitration;

import io.bisq.common.app.Version;
import io.bisq.protobuffer.message.p2p.MailboxMessage;

import java.util.UUID;

public abstract class DisputeMessage implements MailboxMessage {
    //TODO add serialVersionUID also in superclasses as changes would break compatibility
    private final int messageVersion = Version.getP2PMessageVersion();
    private final String uid;

    public DisputeMessage(String uid) {
        this.uid = uid;
    }

    public DisputeMessage() {
        uid = UUID.randomUUID().toString();
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String getUID() {
        return uid;
    }
}
