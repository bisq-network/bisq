package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.data.ProtectedMailboxData;

public final class RemoveMailboxDataMessage extends DataBroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    public final ProtectedMailboxData data;

    public RemoveMailboxDataMessage(ProtectedMailboxData data) {
        this.data = data;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RemoveMailboxDataMessage)) return false;

        RemoveMailboxDataMessage that = (RemoveMailboxDataMessage) o;

        return !(data != null ? !data.equals(that.data) : that.data != null);

    }

    @Override
    public int hashCode() {
        return data != null ? data.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RemoveMailboxDataMessage{" +
                "data=" + data +
                "} " + super.toString();
    }
}
