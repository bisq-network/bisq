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

package bisq.network.p2p.mocks;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.storage.payload.ExpirablePayload;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import org.apache.commons.lang3.NotImplementedException;

import java.util.UUID;

import lombok.Getter;

@Getter
public final class MockMailboxPayload extends NetworkEnvelope implements MailboxMessage, ExpirablePayload {
    private final int messageVersion = Version.getP2PMessageVersion();
    public final String msg;
    public final NodeAddress senderNodeAddress;
    public long ttl = 0;
    private final String uid;

    public MockMailboxPayload(String msg, NodeAddress senderNodeAddress) {
        super(0);
        this.msg = msg;
        this.senderNodeAddress = senderNodeAddress;
        uid = UUID.randomUUID().toString();
    }


    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        throw new NotImplementedException("toProtoNetworkEnvelope not impl.");
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MockMailboxPayload)) return false;

        MockMailboxPayload that = (MockMailboxPayload) o;

        return !(msg != null ? !msg.equals(that.msg) : that.msg != null);

    }

    @Override
    public int hashCode() {
        return msg != null ? msg.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "MockData{" +
                "msg='" + msg + '\'' +
                '}';
    }

    @Override
    public long getTTL() {
        return ttl;
    }

}
