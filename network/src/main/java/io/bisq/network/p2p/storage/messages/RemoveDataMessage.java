package io.bisq.network.p2p.storage.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.Value;

@Value
public final class RemoveDataMessage extends BroadcastMessage {
    private final ProtectedStorageEntry protectedStorageEntry;

    public RemoveDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setRemoveDataMessage(PB.RemoveDataMessage.newBuilder()
                        .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage()))
                .build();
    }

    public static RemoveDataMessage fromProto(PB.RemoveDataMessage proto, NetworkProtoResolver resolver) {
        return new RemoveDataMessage(ProtectedStorageEntry.fromProto(proto.getProtectedStorageEntry(), resolver));
    }
}
