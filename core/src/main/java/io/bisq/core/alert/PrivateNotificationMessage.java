package io.bisq.core.alert;

import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;

@Value
@Slf4j
public class PrivateNotificationMessage implements MailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final PrivateNotificationPayload privateNotificationPayload;
    private final String uid;
    private final int messageVersion = Version.getP2PMessageVersion();

    public PrivateNotificationMessage(PrivateNotificationPayload privateNotificationPayload,
                                      NodeAddress senderNodeAddress,
                                      String uid) {
        this.senderNodeAddress = senderNodeAddress;
        this.privateNotificationPayload = privateNotificationPayload;
        this.uid = uid;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    public static NetworkEnvelope fromProto(PB.PrivateNotificationMessage proto) {
        return new PrivateNotificationMessage(PrivateNotificationPayload.fromProto(proto.getPrivateNotificationPayload()),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid());
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setPrivateNotificationMessage(msgBuilder.getPrivateNotificationMessageBuilder()
                .setMessageVersion(messageVersion)
                .setUid(uid)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setPrivateNotificationPayload(privateNotificationPayload.toProtoMessage())).build();
    }


    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    public PrivateNotificationPayload getPrivateNotificationPayload() {
        return privateNotificationPayload;
    }
}
