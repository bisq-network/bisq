package io.bitsquare.p2p.storage.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.Message;

public final class GetDataRequest implements Message {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.NETWORK_PROTOCOL_VERSION;

    private final int networkId = Version.NETWORK_ID;
    
    public GetDataRequest() {
    }

    @Override
    public int networkId() {
        return networkId;
    }

    @Override
    public String toString() {
        return "GetDataRequest{" +
                "networkId=" + networkId +
                '}';
    }
}
