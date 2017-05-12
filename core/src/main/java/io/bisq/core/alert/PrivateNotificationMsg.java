package io.bisq.core.alert;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMsg;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class PrivateNotificationMsg implements MailboxMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final NodeAddress myNodeAddress;
    public final PrivateNotificationPayload privateNotificationPayload;
    private final String uid;
    private final int messageVersion = Version.getP2PMessageVersion();

    public PrivateNotificationMsg(PrivateNotificationPayload privateNotificationPayload,
                                  NodeAddress myNodeAddress,
                                  String uid) {
        this.myNodeAddress = myNodeAddress;
        this.privateNotificationPayload = privateNotificationPayload;
        this.uid = uid;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return myNodeAddress;
    }

    @Override
    public String getUID() {
        return uid;
    }

    @Override
    public int getMsgVersion() {
        return messageVersion;
    }

    @Override
    public PB.WireEnvelope toProtoMsg() {
        PB.WireEnvelope.Builder msgBuilder = NetworkEnvelope.getMsgBuilder();
        return msgBuilder.setPrivateNotificationMessage(msgBuilder.getPrivateNotificationMessageBuilder()
                .setMessageVersion(messageVersion)
                .setUid(uid)
                .setMyNodeAddress(myNodeAddress.toProtoMessage())
                .setPrivateNotificationPayload(privateNotificationPayload.toProtoMessage())).build();
    }
}
