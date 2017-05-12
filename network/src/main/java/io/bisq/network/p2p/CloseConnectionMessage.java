package io.bisq.network.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;

public final class CloseConnectionMessage implements NetworkEnvelope {
    private final int messageVersion = Version.getP2PMessageVersion();
    public final String reason;

    public CloseConnectionMessage(String reason) {
        this.reason = reason;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "CloseConnectionMessage{" +
                "messageVersion=" + messageVersion +
                ", reason=" + reason +
                '}';
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder envelopeBuilder = NetworkEnvelope.getDefaultBuilder();
        return envelopeBuilder.setCloseConnectionMessage(envelopeBuilder.getCloseConnectionMessageBuilder()
                .setMessageVersion(messageVersion)
                .setReason(reason)).build();
    }
}
