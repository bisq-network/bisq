package io.bisq.network.p2p.storage.messages;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import lombok.Value;

@Value
public final class RemoveMailboxDataMessage extends BroadcastMessage {
    private final ProtectedMailboxStorageEntry protectedMailboxStorageEntry;

    public RemoveMailboxDataMessage(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        this.protectedMailboxStorageEntry = protectedMailboxStorageEntry;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        return msgBuilder.setRemoveMailboxDataMessage(PB.RemoveMailboxDataMessage.newBuilder()
                .setProtectedStorageEntry(protectedMailboxStorageEntry.toProtoMessage())).build();
    }
}
