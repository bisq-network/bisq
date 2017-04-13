package io.bisq.network.p2p;

import io.bisq.common.app.Version;
import io.bisq.common.persistance.Msg;
import io.bisq.generated.protobuffer.PB;

public final class CloseConnectionMsg implements Msg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    // We dont use the Version.NETWORK_PROTOCOL_VERSION here as we report also compatibility issues and
    // a changed version would render that message invalid as well, so the peer cannot get notified about the problem.
    private static final long serialVersionUID = 0;

    private final int messageVersion = Version.getP2PMessageVersion();
    public final String reason;

    public CloseConnectionMsg(String reason) {
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

    //@Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder envelopeBuilder = PB.Envelope.newBuilder()
                .setP2PNetworkVersion(Version.P2P_NETWORK_VERSION);
        return envelopeBuilder.setCloseConnectionMessage(envelopeBuilder.getCloseConnectionMessageBuilder()
                .setMessageVersion(messageVersion)
                .setReason(reason)).build();
    }
}
