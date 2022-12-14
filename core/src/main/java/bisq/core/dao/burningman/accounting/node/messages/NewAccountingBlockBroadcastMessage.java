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

package bisq.core.dao.burningman.accounting.node.messages;


import bisq.core.dao.burningman.accounting.blockchain.AccountingBlock;

import bisq.network.p2p.storage.messages.BroadcastMessage;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.protobuf.ByteString;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Getter
public final class NewAccountingBlockBroadcastMessage extends BroadcastMessage {
    private final AccountingBlock block;
    private final String pubKey;
    private final byte[] signature;

    public NewAccountingBlockBroadcastMessage(AccountingBlock block, String pubKey, byte[] signature) {
        this(block, pubKey, signature, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private NewAccountingBlockBroadcastMessage(AccountingBlock block,
                                               String pubKey,
                                               byte[] signature,
                                               int messageVersion) {
        super(messageVersion);
        this.block = block;
        this.pubKey = pubKey;
        this.signature = signature;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setNewAccountingBlockBroadcastMessage(protobuf.NewAccountingBlockBroadcastMessage.newBuilder()
                        .setBlock(block.toProtoMessage())
                        .setPubKey(pubKey)
                        .setSignature(ByteString.copyFrom(signature))
                )
                .build();
    }

    public static NetworkEnvelope fromProto(protobuf.NewAccountingBlockBroadcastMessage proto, int messageVersion) {
        return new NewAccountingBlockBroadcastMessage(AccountingBlock.fromProto(proto.getBlock()),
                proto.getPubKey(),
                proto.getSignature().toByteArray(),
                messageVersion);
    }
}
