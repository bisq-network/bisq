package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Capabilities;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.common.proto.ProtoCommonUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.AnonymousMessage;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import lombok.Value;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@Value
public final class PreliminaryGetDataRequest implements AnonymousMessage, GetDataRequest, SupportedCapabilitiesMessage {
    private final int nonce;
    private final Set<byte[]> excludedKeys;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public PreliminaryGetDataRequest(int nonce, Set<byte[]> excludedKeys) {
        this.nonce = nonce;
        this.excludedKeys = excludedKeys;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setPreliminaryGetDataRequest(PB.PreliminaryGetDataRequest.newBuilder()
                        .setNonce(nonce)
                        .addAllExcludedKeys(excludedKeys.stream()
                                .map(ByteString::copyFrom)
                                .collect(Collectors.toList()))
                        .addAllSupportedCapabilities(supportedCapabilities))
                .build();
    }

    public static PreliminaryGetDataRequest fromProto(PB.PreliminaryGetDataRequest proto) {
        return new PreliminaryGetDataRequest(proto.getNonce(),
                ProtoCommonUtil.getByteSet(proto.getExcludedKeysList()));
    }
}
