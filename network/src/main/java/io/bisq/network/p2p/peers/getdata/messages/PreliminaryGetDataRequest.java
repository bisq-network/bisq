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
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@EqualsAndHashCode(callSuper = true)
@Value
public final class PreliminaryGetDataRequest extends GetDataRequest implements AnonymousMessage, SupportedCapabilitiesMessage {
    // ordinals of enum
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public PreliminaryGetDataRequest(int nonce,
                                     Set<byte[]> excludedKeys,
                                     @Nullable Set<byte[]> excludedPnpKeys) {
        this(nonce, excludedKeys, Version.getP2PMessageVersion(), excludedPnpKeys);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PreliminaryGetDataRequest(int nonce,
                                      Set<byte[]> excludedKeys,
                                      int messageVersion,
                                      @Nullable Set<byte[]> excludedPnpKeys) {
        super(messageVersion, nonce, excludedKeys, excludedPnpKeys);
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.PreliminaryGetDataRequest.Builder builder = PB.PreliminaryGetDataRequest.newBuilder()
                .setNonce(nonce)
                .addAllExcludedKeys(excludedKeys.stream()
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()))
                .addAllSupportedCapabilities(supportedCapabilities);

        Optional.ofNullable(excludedPnpKeys).ifPresent(excludedPnpKeys -> builder.addAllExcludedPnpKeys(excludedPnpKeys.stream()
                .map(ByteString::copyFrom)
                .collect(Collectors.toList())));

        return getNetworkEnvelopeBuilder()
                .setPreliminaryGetDataRequest(builder)
                .build();
    }

    public static PreliminaryGetDataRequest fromProto(PB.PreliminaryGetDataRequest proto, int messageVersion) {
        return new PreliminaryGetDataRequest(proto.getNonce(),
                ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList()),
                messageVersion,
                proto.getExcludedPnpKeysList().isEmpty() ?
                        null :
                        ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedPnpKeysList()));
    }
}
