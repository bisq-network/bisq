package io.bisq.core.dao.blockchain.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBsqBlocksRequest extends NetworkEnvelope implements DirectMessage {
    private final int fromBlockHeight;

    public GetBsqBlocksRequest(int fromBlockHeight) {
        this(fromBlockHeight, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksRequest(int fromBlockHeight, int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksRequest(PB.GetBsqBlocksRequest.newBuilder()
                        .setFromBlockHeight(fromBlockHeight))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksRequest proto, int messageVersion) {
        return new GetBsqBlocksRequest(proto.getFromBlockHeight(), messageVersion);
    }
}
