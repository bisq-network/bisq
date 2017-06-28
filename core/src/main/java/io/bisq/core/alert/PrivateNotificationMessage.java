package io.bisq.core.alert;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.MailboxMessage;
import io.bisq.network.p2p.NodeAddress;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public class PrivateNotificationMessage extends NetworkEnvelope implements MailboxMessage {
    private final PrivateNotificationPayload privateNotificationPayload;
    private final NodeAddress senderNodeAddress;
    private final String uid;

    public PrivateNotificationMessage(PrivateNotificationPayload privateNotificationPayload,
                                      NodeAddress senderNodeAddress,
                                      String uid) {
        this(privateNotificationPayload, senderNodeAddress, uid, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private PrivateNotificationMessage(PrivateNotificationPayload privateNotificationPayload,
                                       NodeAddress senderNodeAddress,
                                       String uid,
                                       int messageVersion) {
        super(messageVersion);
        this.privateNotificationPayload = privateNotificationPayload;
        this.senderNodeAddress = senderNodeAddress;
        this.uid = uid;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPrivateNotificationMessage(PB.PrivateNotificationMessage.newBuilder()
                        .setPrivateNotificationPayload(privateNotificationPayload.toProtoMessage())
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setUid(uid))
                .build();
    }

    public static PrivateNotificationMessage fromProto(PB.PrivateNotificationMessage proto, int messageVersion) {
        return new PrivateNotificationMessage(PrivateNotificationPayload.fromProto(proto.getPrivateNotificationPayload()),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getUid(),
                messageVersion);
    }
}
