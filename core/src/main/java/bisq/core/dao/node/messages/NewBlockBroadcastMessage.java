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

package bisq.core.dao.node.messages;

import bisq.core.dao.node.full.RawBlock;

import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import lombok.EqualsAndHashCode;
import lombok.Getter;

// We remove the CapabilityRequiringPayload interface to avoid risks that new BSQ blocks are not well distributed in
// case the capability is not exchanged at the time when the message is sent. We need to improve the capability handling
// so that we can be sure that we know the actual capability of the peer.

// This message is sent only to lite DAO nodes (full nodes get block from their local bitcoind)
@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewBlockBroadcastMessage extends BroadcastMessage /*implements CapabilityRequiringPayload*/ {
    private final RawBlock block;

    public NewBlockBroadcastMessage(RawBlock block) {
        this(block, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewBlockBroadcastMessage(RawBlock block, int messageVersion) {
        super(messageVersion);
        this.block = block;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewBlockBroadcastMessage(protobuf.NewBlockBroadcastMessage.newBuilder()
                        .setRawBlock(block.toProtoMessage()))
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.NewBlockBroadcastMessage proto, int messageVersion) {
        return new NewBlockBroadcastMessage(RawBlock.fromProto(proto.getRawBlock()),
                messageVersion);
    }

//    @Override
//    public Capabilities getRequiredCapabilities() {
//        return new Capabilities(Capability.RECEIVE_BSQ_BLOCK);
//    }
}
