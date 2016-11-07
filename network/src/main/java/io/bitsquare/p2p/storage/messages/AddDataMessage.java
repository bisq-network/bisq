package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

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
        if (o == null || this.getClass() != o.getClass()) return false;

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
}
