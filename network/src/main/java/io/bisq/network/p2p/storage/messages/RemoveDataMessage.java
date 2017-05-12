package io.bisq.network.p2p.storage.messages;

import io.bisq.common.network.NetworkEnvelope;
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
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setRemoveDataMessage(PB.RemoveDataMessage.newBuilder()
                .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage())).build();

    }
}
