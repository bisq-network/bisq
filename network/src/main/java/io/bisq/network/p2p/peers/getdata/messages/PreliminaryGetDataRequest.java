package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.AnonymousMessage;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public final class PreliminaryGetDataRequest extends GetDataRequest implements AnonymousMessage, SupportedCapabilitiesMessage {
    // ordinals of enum
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public PreliminaryGetDataRequest(int nonce, Set<byte[]> excludedKeys) {
        this(nonce, excludedKeys, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PreliminaryGetDataRequest(int nonce, Set<byte[]> excludedKeys, int messageVersion) {
        super(messageVersion, nonce, excludedKeys);
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPreliminaryGetDataRequest(PB.PreliminaryGetDataRequest.newBuilder()
                        .setNonce(nonce)
                        .addAllExcludedKeys(excludedKeys.stream()
                                .map(ByteString::copyFrom)
                                .collect(Collectors.toList()))
                        .addAllSupportedCapabilities(supportedCapabilities))
                .build();
    }

    public static PreliminaryGetDataRequest fromProto(PB.PreliminaryGetDataRequest proto, int messageVersion) {
        return new PreliminaryGetDataRequest(proto.getNonce(),
                ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList()),
                messageVersion);
    }
}
