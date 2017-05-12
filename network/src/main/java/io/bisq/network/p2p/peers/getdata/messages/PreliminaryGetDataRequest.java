package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Capabilities;
import io.bisq.common.network.NetworkEnvelope;
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
        PB.NetworkEnvelope.Builder envelopeBuilder = NetworkEnvelope.getDefaultBuilder();
        PB.PreliminaryGetDataRequest.Builder msgBuilder = envelopeBuilder.getPreliminaryGetDataRequestBuilder()
                .setMessageVersion(getMessageVersion())
                .setNonce(nonce);
        msgBuilder.addAllSupportedCapabilities(supportedCapabilities);

        msgBuilder.addAllExcludedKeys(excludedKeys.stream().map(ByteString::copyFrom).collect(Collectors.toList()));
        return envelopeBuilder.setPreliminaryGetDataRequest(msgBuilder).build();
    }
}
