package io.bisq.core.dao.blockchain.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import lombok.Getter;

@Getter
public final class GetBsqBlocksRequest implements DirectMessage {
    private final int messageVersion = Version.getP2PMessageVersion();
    private final int fromBlockHeight;

    public GetBsqBlocksRequest(int fromBlockHeight) {
        this.fromBlockHeight = fromBlockHeight;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setGetBsqBlocksRequest(PB.GetBsqBlocksRequest.newBuilder()
                        .setFromBlockHeight(fromBlockHeight))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksRequest proto) {
        return new GetBsqBlocksRequest(proto.getFromBlockHeight());
    }
}
