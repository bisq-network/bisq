package io.bisq.message.p2p.storage;

import io.bisq.app.Version;
import io.bisq.common.wire.proto.Messages;
import io.bisq.payload.p2p.storage.ProtectedStorageEntry;
import io.bisq.util.ProtoBufferUtils;

public final class RemoveDataMessage extends BroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

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

    @Override
    public Messages.Envelope toProtoBuf() {
        Messages.Envelope.Builder baseEnvelope = ProtoBufferUtils.getBaseEnvelope();
        return baseEnvelope.setRemoveDataMessage(Messages.RemoveDataMessage.newBuilder()
                .setProtectedStorageEntry((Messages.ProtectedStorageEntry) protectedStorageEntry.toProtoBuf())).build();

    }
}
