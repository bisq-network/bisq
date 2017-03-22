package io.bisq.protobuffer.message.p2p.storage;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedMailboxStorageEntry;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedStorageEntry;

public final class AddDataMessage extends BroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final ProtectedStorageEntry protectedStorageEntry;

    public AddDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddDataMessage)) return false;

        AddDataMessage that = (AddDataMessage) o;

        return !(protectedStorageEntry != null ? !protectedStorageEntry.equals(that.protectedStorageEntry) : that.protectedStorageEntry != null);
    }

    @Override
    public int hashCode() {
        return protectedStorageEntry != null ? protectedStorageEntry.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "AddDataMessage{" +
                "protectedStorageEntry=" + protectedStorageEntry +
                "} " + super.toString();
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        PB.AddDataMessage.Builder builder;
        PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder choiceBuilder;
        choiceBuilder = PB.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();

        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
            builder = PB.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedMailboxStorageEntry((PB.ProtectedMailboxStorageEntry) protectedStorageEntry.toProto()));
        } else {
            builder = PB.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedStorageEntry((PB.ProtectedStorageEntry) protectedStorageEntry.toProto()));
        }
        return baseEnvelope.setAddDataMessage(builder).build();
    }
}
