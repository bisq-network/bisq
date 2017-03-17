package io.bisq.wire.message.p2p.storage;

import io.bisq.common.app.Version;
import io.bisq.wire.message.ToProtoBuffer;
import io.bisq.wire.payload.p2p.storage.ProtectedMailboxStorageEntry;
import io.bisq.wire.payload.p2p.storage.ProtectedStorageEntry;
import io.bisq.wire.proto.Messages;

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
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ToProtoBuffer.getBaseEnvelope();
        Messages.AddDataMessage.Builder builder;
        Messages.ProtectedStorageEntryOrProtectedMailboxStorageEntry.Builder choiceBuilder;
        choiceBuilder = Messages.ProtectedStorageEntryOrProtectedMailboxStorageEntry.newBuilder();

        if (protectedStorageEntry instanceof ProtectedMailboxStorageEntry) {
            builder = Messages.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedMailboxStorageEntry((Messages.ProtectedMailboxStorageEntry) protectedStorageEntry.toProtoBuf()));
        } else {
            builder = Messages.AddDataMessage.newBuilder().setEntry(
                    choiceBuilder.setProtectedStorageEntry((Messages.ProtectedStorageEntry) protectedStorageEntry.toProtoBuf()));
        }
        return baseEnvelope.setAddDataMessage(builder).build();
    }
}
