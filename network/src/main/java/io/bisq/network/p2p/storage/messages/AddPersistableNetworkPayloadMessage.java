package io.bisq.network.p2p.storage.messages;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.PersistableNetworkPayload;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class AddPersistableNetworkPayloadMessage extends BroadcastMessage {
    private final PersistableNetworkPayload persistableNetworkPayload;

    public AddPersistableNetworkPayloadMessage(PersistableNetworkPayload persistableNetworkPayload) {
        this(persistableNetworkPayload, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddPersistableNetworkPayloadMessage(PersistableNetworkPayload persistableNetworkPayload, int messageVersion) {
        super(messageVersion);
        this.persistableNetworkPayload = persistableNetworkPayload;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setAddPersistableNetworkPayloadMessage(PB.AddPersistableNetworkPayloadMessage.newBuilder()
                        .setPayload(persistableNetworkPayload.toProtoMessage()))
                .build();
    }

    public static AddPersistableNetworkPayloadMessage fromProto(PB.AddPersistableNetworkPayloadMessage proto,
                                                                NetworkProtoResolver resolver,
                                                                int messageVersion) {
        return new AddPersistableNetworkPayloadMessage((PersistableNetworkPayload) resolver.fromProto(proto.getPayload()),
                messageVersion);
    }
}
