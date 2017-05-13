package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import lombok.Getter;

@Getter
public final class GetBsqBlocksResponse implements DirectMessage, ExtendedDataSizePermission {
    private final int messageVersion = Version.getP2PMessageVersion();

    private final byte[] bsqBlocksBytes;

    public GetBsqBlocksResponse(byte[] bsqBlocksBytes) {
        this.bsqBlocksBytes = bsqBlocksBytes;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setGetBsqBlocksResponse(PB.GetBsqBlocksResponse.newBuilder()
                        .setBsqBlocksBytes(ByteString.copyFrom(bsqBlocksBytes)))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksResponse proto) {
        return new GetBsqBlocksResponse(proto.getBsqBlocksBytes().toByteArray());
    }
}
