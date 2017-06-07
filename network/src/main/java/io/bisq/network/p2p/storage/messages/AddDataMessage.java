package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.Message;
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
        PB.StorageEntryWrapper.Builder builder = PB.StorageEntryWrapper.newBuilder();
        final Message message = protectedStorageEntry.toProtoMessage();
        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry)
            builder.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) message);
        else
            builder.setProtectedStorageEntry((PB.ProtectedStorageEntry) message);

        return NetworkEnvelope.getDefaultBuilder()
                .setAddDataMessage(PB.AddDataMessage.newBuilder()
                        .setEntry(builder))
                .build();
    }

    public static AddDataMessage fromProto(PB.AddDataMessage proto, NetworkProtoResolver resolver) {
        return new AddDataMessage((ProtectedStorageEntry) resolver.fromProto(proto.getEntry()));
    }
}
