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

package bisq.core.arbitration.messages;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.UidMessage;

import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public abstract class DisputeMessage extends NetworkEnvelope implements MailboxMessage, UidMessage {
    protected final String uid;

    public DisputeMessage(int messageVersion, String uid) {
        super(messageVersion);
        this.uid = uid;
    }

    public abstract String getTradeId();

    @Override
    public String toString() {
        return "DisputeMessage{" +
                "\n     uid='" + uid + '\'' +
                ",\n     messageVersion=" + messageVersion +
                "\n} " + super.toString();
    }
}
