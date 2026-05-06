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
import bisq.network.p2p.SendersSignaturePubKeyProvidingPayload;
import bisq.network.p2p.mailbox.MailboxMessage;
import bisq.network.p2p.storage.payload.ExpirablePayload;

import bisq.common.proto.network.NetworkEnvelope;

import org.apache.commons.lang3.NotImplementedException;

import java.security.PublicKey;

import java.util.UUID;

import lombok.Getter;

@Getter
public final class MockSignaturePubKeyProvidingMailboxPayload extends NetworkEnvelope
        implements MailboxMessage, ExpirablePayload, SendersSignaturePubKeyProvidingPayload {
    private final String msg;
    private final NodeAddress senderNodeAddress;
    private final PublicKey senderSignaturePubKey;
    private final String uid;
    public long ttl = 0;

    public MockSignaturePubKeyProvidingMailboxPayload(String msg,
                                                      NodeAddress senderNodeAddress,
                                                      PublicKey senderSignaturePubKey) {
        super(0);
        this.msg = msg;
        this.senderNodeAddress = senderNodeAddress;
        this.senderSignaturePubKey = senderSignaturePubKey;
        uid = UUID.randomUUID().toString();
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        throw new NotImplementedException("toProtoNetworkEnvelope not impl.");
    }

    @Override
    public long getTTL() {
        return ttl;
    }
}
