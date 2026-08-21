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

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.ExtendedDataSizePermission;
import bisq.network.p2p.InitialDataRequest;
import bisq.network.p2p.InitialDataResponse;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import com.google.protobuf.ByteString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

// Taken from GetBlocksResponse
@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class GetAccountingBlocksResponse extends NetworkEnvelope implements DirectMessage,
        ExtendedDataSizePermission, InitialDataResponse {
    private final List<AccountingBlock> blocks;
    private final int requestNonce;
    private final String pubKey;
    private final byte[] signature;

    public GetAccountingBlocksResponse(List<AccountingBlock> blocks,
                                       int requestNonce,
                                       String pubKey,
                                       byte[] signature) {
        this(blocks, requestNonce, pubKey, signature, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetAccountingBlocksResponse(List<AccountingBlock> blocks,
                                        int requestNonce,
                                        String pubKey,
                                        byte[] signature,
                                        int messageVersion) {
        super(messageVersion);

        this.blocks = blocks;
        this.requestNonce = requestNonce;
        this.pubKey = pubKey;
        this.signature = signature;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.NetworkEnvelope proto = getNetworkEnvelopeBuilder()
                .setGetAccountingBlocksResponse(protobuf.GetAccountingBlocksResponse.newBuilder()
                        .addAllBlocks(blocks.stream()
                                .map(AccountingBlock::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce)
                        .setPubKey(pubKey)
                        .setSignature(ByteString.copyFrom(signature)))
                .build();
        log.info("Sending a GetBlocksResponse with {} kB", proto.getSerializedSize() / 1000d);
        return proto;
    }

    public static NetworkEnvelope fromProto(protobuf.GetAccountingBlocksResponse proto, int messageVersion) {
        List<AccountingBlock> list = proto.getBlocksList().stream()
                .map(AccountingBlock::fromProto)
                .collect(Collectors.toList());
        log.info("Received a GetBlocksResponse with {} blocks and {} kB size", list.size(), proto.getSerializedSize() / 1000d);
        return new GetAccountingBlocksResponse(proto.getBlocksList().isEmpty() ?
                new ArrayList<>() :
                list,
                proto.getRequestNonce(),
                proto.getPubKey(),
                proto.getSignature().toByteArray(),
                messageVersion);
    }

    @Override
    public Class<? extends InitialDataRequest> associatedRequest() {
        return GetAccountingBlocksRequest.class;
    }
}
