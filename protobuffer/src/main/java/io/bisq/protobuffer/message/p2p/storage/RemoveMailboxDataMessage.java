package io.bisq.protobuffer.message.p2p.storage;

import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.Message;
import io.bisq.protobuffer.payload.p2p.storage.ProtectedMailboxStorageEntry;

public final class RemoveMailboxDataMessage extends BroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final ProtectedMailboxStorageEntry protectedMailboxStorageEntry;

    public RemoveMailboxDataMessage(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        this.protectedMailboxStorageEntry = protectedMailboxStorageEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoveMailboxDataMessage)) return false;

        RemoveMailboxDataMessage that = (RemoveMailboxDataMessage) o;

        return !(protectedMailboxStorageEntry != null ? !protectedMailboxStorageEntry.equals(that.protectedMailboxStorageEntry) : that.protectedMailboxStorageEntry != null);
    }

    @Override
    public int hashCode() {
        return protectedMailboxStorageEntry != null ? protectedMailboxStorageEntry.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RemoveMailboxDataMessage{" +
                "data=" + protectedMailboxStorageEntry +
                "} " + super.toString();
    }

    @Override
    public PB.Envelope toProto() {
        PB.Envelope.Builder baseEnvelope = Message.getBaseEnvelope();
        return baseEnvelope.setRemoveMailboxDataMessage(PB.RemoveMailboxDataMessage.newBuilder()
                .setProtectedStorageEntry(protectedMailboxStorageEntry.toProto())).build();

    }
}
