package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.storageentry.ProtectedMailboxStorageEntry;

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
        if (o == null || this.getClass() != o.getClass()) return false;

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
}
