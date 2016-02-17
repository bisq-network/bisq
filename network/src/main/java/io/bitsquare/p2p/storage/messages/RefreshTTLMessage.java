package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.storage.data.RefreshTTLBundle;

public final class RefreshTTLMessage extends DataBroadcastMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    public final RefreshTTLBundle refreshTTLBundle;

    public RefreshTTLMessage(RefreshTTLBundle refreshTTLBundle) {
        this.refreshTTLBundle = refreshTTLBundle;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RefreshTTLMessage)) return false;

        RefreshTTLMessage that = (RefreshTTLMessage) o;

        return !(refreshTTLBundle != null ? !refreshTTLBundle.equals(that.refreshTTLBundle) : that.refreshTTLBundle != null);

    }

    @Override
    public int hashCode() {
        return refreshTTLBundle != null ? refreshTTLBundle.hashCode() : 0;
    }

    @Override
    public String toString() {
        return "RefreshTTLMessage{" +
                "refreshTTLPackage=" + refreshTTLBundle +
                "} " + super.toString();
    }
}
