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
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class PreliminaryGetDataRequest extends GetDataRequest implements AnonymousMessage, SupportedCapabilitiesMessage {
    // ordinals of enum
    @Nullable
    private final List<Integer> supportedCapabilities;

    public PreliminaryGetDataRequest(int nonce,
                                     Set<byte[]> excludedKeys) {
        this(nonce, excludedKeys, Capabilities.getSupportedCapabilities(), Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PreliminaryGetDataRequest(int nonce,
                                      Set<byte[]> excludedKeys,
                                      @Nullable List<Integer> supportedCapabilities,
                                      int messageVersion) {
        super(messageVersion, nonce, excludedKeys);

        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.PreliminaryGetDataRequest.Builder builder = PB.PreliminaryGetDataRequest.newBuilder()
                .setNonce(nonce)
                .addAllExcludedKeys(excludedKeys.stream()
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(supportedCapabilities));

        return getNetworkEnvelopeBuilder()
                .setPreliminaryGetDataRequest(builder)
                .build();
    }

    public static PreliminaryGetDataRequest fromProto(PB.PreliminaryGetDataRequest proto, int messageVersion) {
        return new PreliminaryGetDataRequest(proto.getNonce(),
                ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList()),
                proto.getSupportedCapabilitiesList().isEmpty() ? null : proto.getSupportedCapabilitiesList(),
                messageVersion);
    }
}
