package io.bisq.core.dao.blockchain.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
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
    public int getMessageVersion() {
        return messageVersion;
    }


    @Override
    public PB.Envelope toEnvelopeProto() {
        final PB.GetBsqBlocksRequest.Builder builder = PB.GetBsqBlocksRequest.newBuilder()
                .setFromBlockHeight(fromBlockHeight);
        return Msg.getEnv().setGetBsqBlocksRequest(builder).build();
    }

    public static Msg fromProto(PB.Envelope envelope) {
        PB.GetBsqBlocksRequest msg = envelope.getGetBsqBlocksRequest();
        return new GetBsqBlocksRequest(msg.getFromBlockHeight());
    }
}
