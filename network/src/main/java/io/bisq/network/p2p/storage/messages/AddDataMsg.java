package io.bisq.network.p2p.storage.messages;

import com.google.protobuf.Message;
import io.bisq.common.app.Version;
import io.bisq.common.network.Msg;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;
import io.bisq.network.p2p.storage.payload.ProtectedStorageEntry;

public final class AddDataMsg extends BroadcastMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final ProtectedStorageEntry protectedStorageEntry;

    public AddDataMsg(ProtectedStorageEntry protectedStorageEntry) {
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AddDataMsg)) return false;

        AddDataMsg that = (AddDataMsg) o;

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
    public PB.Envelope toEnvelopeProto() {
        PB.Envelope.Builder baseEnvelope = Msg.getEnv();
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

    @Override
    public Message toProto() {
        return toEnvelopeProto().getAddDataMessage();
    }
}
