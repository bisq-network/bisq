package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.storageentry.ProtectedStorageEntry;

import javax.annotation.Nullable;
import java.util.ArrayList;

public final class RemoveDataMessage extends BroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    public final ProtectedStorageEntry protectedStorageEntry;

    public RemoveDataMessage(ProtectedStorageEntry protectedStorageEntry) {
        this.protectedStorageEntry = protectedStorageEntry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoveDataMessage)) return false;

        RemoveDataMessage that = (RemoveDataMessage) o;

        return !(protectedStorageEntry != null ? !protectedStorageEntry.equals(that.protectedStorageEntry) : that.protectedStorageEntry != null);

    }

    @Override
    public int hashCode() {
        return protectedStorageEntry != null ? protectedStorageEntry.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RemoveDataMessage{" +
                "protectedStorageEntry=" + protectedStorageEntry +
                "} " + super.toString();
    }
}
