package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.Message;
import io.bisq.common.network.NetworkEnvelope;
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
        PB.NetworkEnvelope.Builder msgBuilder = NetworkEnvelope.getDefaultBuilder();
        PB.AddDataMessage.Builder builder;
        PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder choiceBuilder;
        choiceBuilder = PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();

        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
            builder = PB.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoMessage()));
        } else {
            builder = PB.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProtoMessage()));
        }
        return msgBuilder.setAddDataMessage(builder).build();
    }

    @Override
    public Message toProtoMessage() {
        return toProtoNetworkEnvelope().getAddDataMessage();
    }
}
