package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import com.google.protobuf.Message;
import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.AnonymousMessage;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Set;
import java.util.stream.Collectors;

public final class PreliminaryGetDataRequest implements AnonymousMessage, GetDataRequest, SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final int nonce;
    private final Set<byte[]> excludedKeys;
    @Nullable
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public PreliminaryGetDataRequest(int nonce, Set<byte[]> excludedKeys) {
        this.nonce = nonce;
        this.excludedKeys = excludedKeys;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public Set<byte[]> getExcludedKeys() {
        return excludedKeys;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "PreliminaryGetDataRequest{" +
                "nonce=" + nonce +
                ", supportedCapabilities=" + supportedCapabilities +
                ", messageVersion=" + messageVersion +
                '}';
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder envelopeBuilder = NetworkEnvelope.getDefaultBuilder();
        PB.PreliminaryGetDataRequest.Builder msgBuilder = envelopeBuilder.getPreliminaryGetDataRequestBuilder()
                .setMessageVersion(messageVersion)
                .setNonce(nonce);
        msgBuilder.addAllSupportedCapabilities(supportedCapabilities);
        msgBuilder.addAllExcludedKeys(excludedKeys.stream().map(ByteString::copyFrom).collect(Collectors.toList()));
        return envelopeBuilder.setPreliminaryGetDataRequest(msgBuilder).build();
    }

    @Override
    public Message toProtoMessage() {
        return toProtoNetworkEnvelope().getPreliminaryGetDataRequest();
    }
}
