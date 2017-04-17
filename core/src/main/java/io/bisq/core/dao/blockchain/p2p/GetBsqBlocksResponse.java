package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
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
    public PB.Envelope toProto() {
        final PB.GetBsqBlocksResponse.Builder builder = PB.GetBsqBlocksResponse.newBuilder()
                .setBsqBlocksBytes(ByteString.copyFrom(bsqBlocksBytes));
        return Msg.getEnv().setGetBsqBlocksResponse(builder).build();
    }

    public static Msg fromProto(PB.Envelope envelope) {
        PB.GetBsqBlocksResponse msg = envelope.getGetBsqBlocksResponse();
        return new GetBsqBlocksResponse(msg.getBsqBlocksBytes().toByteArray());
    }
}
