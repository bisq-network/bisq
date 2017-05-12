package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMsg;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import lombok.Getter;

@Getter
public final class GetBsqBlocksResponse implements DirectMsg, ExtendedDataSizePermission {
    private final int messageVersion = Version.getP2PMessageVersion();

    private final byte[] bsqBlocksBytes;

    public GetBsqBlocksResponse(byte[] bsqBlocksBytes) {
        this.bsqBlocksBytes = bsqBlocksBytes;
    }

    @Override
    public int getMsgVersion() {
        return messageVersion;
    }

    @Override
    public PB.WireEnvelope toProtoMsg() {
        final PB.GetBsqBlocksResponse.Builder builder = PB.GetBsqBlocksResponse.newBuilder()
                .setBsqBlocksBytes(ByteString.copyFrom(bsqBlocksBytes));
        return NetworkEnvelope.getMsgBuilder().setGetBsqBlocksResponse(builder).build();
    }

    public static NetworkEnvelope fromProto(PB.WireEnvelope msg) {
        return new GetBsqBlocksResponse(msg.getGetBsqBlocksResponse().getBsqBlocksBytes().toByteArray());
    }
}
