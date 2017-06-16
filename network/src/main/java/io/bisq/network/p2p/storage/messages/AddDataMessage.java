package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkProtoResolver;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;
import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class AddDataMessage extends BroadcastMessage {
    private final ProtectedStorageEntry protectedStorageEntry;

    public AddDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this(protectedStorageEntry, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private AddDataMessage(ProtectedStorageEntry protectedStorageEntry, int messageVersion) {
        super(messageVersion);
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

        return getNetworkEnvelopeBuilder()
                .setAddDataMessage(PB.AddDataMessage.newBuilder()
                        .setEntry(builder))
                .build();
    }

    public static AddDataMessage fromProto(PB.AddDataMessage proto, NetworkProtoResolver resolver, int messageVersion) {
        return new AddDataMessage((ProtectedStorageEntry) resolver.fromProto(proto.getEntry()), messageVersion);
    }
}
