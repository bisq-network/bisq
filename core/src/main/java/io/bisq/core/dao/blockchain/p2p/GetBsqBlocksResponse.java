package io.bisq.core.dao.blockchain.p2p;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import io.bisq.network.p2p.ExtendedDataSizePermission;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBsqBlocksResponse extends NetworkEnvelope implements DirectMessage, ExtendedDataSizePermission {
    private final byte[] bsqBlocksBytes;

    public GetBsqBlocksResponse(byte[] bsqBlocksBytes) {
        this(bsqBlocksBytes, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksResponse(byte[] bsqBlocksBytes, int messageVersion) {
        super(messageVersion);
        this.bsqBlocksBytes = bsqBlocksBytes;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksResponse(PB.GetBsqBlocksResponse.newBuilder()
                        .setBsqBlocksBytes(ByteString.copyFrom(bsqBlocksBytes)))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksResponse proto, int messageVersion) {
        return new GetBsqBlocksResponse(proto.getBsqBlocksBytes().toByteArray(), messageVersion);
    }
}
