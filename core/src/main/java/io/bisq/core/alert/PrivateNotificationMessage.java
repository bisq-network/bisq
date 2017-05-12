package io.bisq.core.alert;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@EqualsAndHashCode
@ToString
@Slf4j
public class PrivateNotificationMessage implements MailboxMessage {
    private final NodeAddress myNodeAddress;
    public final PrivateNotificationPayload privateNotificationPayload;
    private final String uid;
    private final int messageVersion = Version.getP2PMessageVersion();

    public PrivateNotificationMessage(PrivateNotificationPayload privateNotificationPayload,
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
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setPrivateNotificationMessage(msgBuilder.getPrivateNotificationMessageBuilder()
                .setMessageVersion(messageVersion)
                .setUid(uid)
                .setMyNodeAddress(myNodeAddress.toProtoMessage())
                .setPrivateNotificationPayload(privateNotificationPayload.toProtoMessage())).build();
    }
}
