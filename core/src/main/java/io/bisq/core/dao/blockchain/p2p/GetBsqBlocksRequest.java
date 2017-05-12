package io.bisq.core.dao.blockchain.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMsg;
import lombok.Getter;

@Getter
public final class GetBsqBlocksRequest implements DirectMsg {
    private final int messageVersion = Version.getP2PMessageVersion();
    private final int fromBlockHeight;

    public GetBsqBlocksRequest(int fromBlockHeight) {
        this.fromBlockHeight = fromBlockHeight;
    }

    @Override
    public int getMsgVersion() {
        return messageVersion;
    }


    @Override
    public PB.WireEnvelope toProtoMsg() {
        final PB.GetBsqBlocksRequest.Builder builder = PB.GetBsqBlocksRequest.newBuilder()
                .setFromBlockHeight(fromBlockHeight);
        return NetworkEnvelope.getMsgBuilder().setGetBsqBlocksRequest(builder).build();
    }

    public static NetworkEnvelope fromProto(PB.WireEnvelope msg) {
        return new GetBsqBlocksRequest(msg.getGetBsqBlocksRequest().getFromBlockHeight());
    }
}
