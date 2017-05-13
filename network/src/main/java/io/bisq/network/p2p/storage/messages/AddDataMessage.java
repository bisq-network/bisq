package io.bisq.network.p2p.storage.messages;

import io.bisq.common.network.NetworkEnvelope;
import io.bisq.common.proto.NetworkProtoResolver;
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
        final PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder builder = PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();
        PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder entry;
        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
            entry = builder.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage());
        else
            entry = builder.setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage());

        return NetworkEnvelope.getDefaultBuilder()
                .setAddDataMessage(PB.AddDataMessage.newBuilder().setEntry(entry))
                .build();
    }

    public static AddDataMessage fromProto(PB.AddDataMessage proto, NetworkProtoResolver resolver) {
        return new AddDataMessage((ProtectedStorageEntry) resolver.mapToProtectedStorageEntry(proto.getEntry()));
    }
}
