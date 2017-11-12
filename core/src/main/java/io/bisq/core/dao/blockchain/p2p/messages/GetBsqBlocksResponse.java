package io.bisq.core.dao.blockchain.p2p.messages;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.core.dao.blockchain.vo.BsqBlock;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBsqBlocksResponse extends NetworkEnvelope implements DirectMessage, ExtendedDataSizePermission {
    private final List<BsqBlock> bsqBlocks;
    private final int requestNonce;

    public GetBsqBlocksResponse(List<BsqBlock> bsqBlocks, int requestNonce) {
        this(bsqBlocks, requestNonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksResponse(List<BsqBlock> bsqBlocks, int requestNonce, int messageVersion) {
        super(messageVersion);
        this.bsqBlocks = bsqBlocks;
        this.requestNonce = requestNonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksResponse(PB.GetBsqBlocksResponse.newBuilder()
                        .addAllBsqBlocks(bsqBlocks.stream()
                                .map(BsqBlock::toProtoMessage)
                                .collect(Collectors.toList()))
                        .setRequestNonce(requestNonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksResponse proto, int messageVersion) {
        return new GetBsqBlocksResponse(proto.getBsqBlocksList().isEmpty() ?
                new ArrayList<>() :
                proto.getBsqBlocksList().stream()
                        .map(BsqBlock::fromProto)
                        .collect(Collectors.toList()),
                proto.getRequestNonce(),
                messageVersion);
    }
}
