package io.bisq.network.p2p.storage.messages;

import io.bisq.common.app.Version;
import io.bisq.common.persistance.Msg;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.storage.payload.ProtectedMailboxStorageEntry;


public final class RemoveMailboxDataMsg extends BroadcastMsg {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final ProtectedMailboxStorageEntry protectedMailboxStorageEntry;

    public RemoveMailboxDataMsg(ProtectedMailboxStorageEntry protectedMailboxStorageEntry) {
        this.protectedMailboxStorageEntry = protectedMailboxStorageEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoveMailboxDataMsg)) return false;

        RemoveMailboxDataMsg that = (RemoveMailboxDataMsg) o;

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
        PB.Envelope.Builder baseEnvelope = Msg.getEnv();
        return baseEnvelope.setRemoveMailboxDataMessage(PB.RemoveMailboxDataMessage.newBuilder()
                .setProtectedStorageEntry(protectedMailboxStorageEntry.toProto())).build();

    }
}
