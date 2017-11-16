package io.bisq.core.dao.blockchain.p2p.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.DirectMessage;
import io.bisq.network.p2p.storage.payload.CapabilityRequiringPayload;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBsqBlocksRequest extends NetworkEnvelope implements DirectMessage, CapabilityRequiringPayload {
    private final int fromBlockHeight;
    private final int nonce;

    public GetBsqBlocksRequest(int fromBlockHeight, int nonce) {
        this(fromBlockHeight, nonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBsqBlocksRequest(int fromBlockHeight, int nonce, int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
        this.nonce = nonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBsqBlocksRequest(PB.GetBsqBlocksRequest.newBuilder()
                        .setFromBlockHeight(fromBlockHeight)
                        .setNonce(nonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBsqBlocksRequest proto, int messageVersion) {
        return new GetBsqBlocksRequest(proto.getFromBlockHeight(), proto.getNonce(), messageVersion);
    }

    @Override
    public List<Integer> getRequiredCapabilities() {
        return new ArrayList<>(Collections.singletonList(
                Capabilities.Capability.DAO_FULL_NODE.ordinal()
        ));
    }
}
