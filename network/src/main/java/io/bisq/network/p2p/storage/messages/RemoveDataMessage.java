package io.bisq.network.p2p.storage.messages;

import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class RemoveDataMessage extends BroadcastMessage {
    private final ProtectedStorageEntry protectedStorageEntry;

    public RemoveDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this(protectedStorageEntry, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private RemoveDataMessage(ProtectedStorageEntry protectedStorageEntry,
                              int messageVersion) {
        super(messageVersion);
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setRemoveDataMessage(PB.RemoveDataMessage.newBuilder()
                        .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage()))
                .build();
    }

    public static RemoveDataMessage fromProto(PB.RemoveDataMessage proto, NetworkProtoResolver resolver, int messageVersion) {
        return new RemoveDataMessage(ProtectedStorageEntry.fromProto(proto.getProtectedStorageEntry(), resolver), messageVersion);
    }
}
