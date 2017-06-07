package io.bisq.network.p2p.storage.messages;

import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.Value;

@Value
public final class AddDataMessage extends BroadcastMessage {
    private final ProtectedStorageEntry protectedStorageEntry;

    public AddDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        PB.StorageEntryWrapper.Builder builder;
        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
            builder = PB.StorageEntryWrapper.newBuilder()
                    .setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage());
        else
            builder = PB.StorageEntryWrapper.newBuilder()
                    .setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage());

        return NetworkEnvelope.getDefaultBuilder()
                .setAddDataMessage(PB.AddDataMessage.newBuilder().setEntry(builder))
                .build();
    }

    public static AddDataMessage fromProto(PB.AddDataMessage proto, NetworkProtoResolver resolver) {
        return new AddDataMessage((ProtectedStorageEntry) resolver.fromProto(proto.getEntry()));
    }
}
