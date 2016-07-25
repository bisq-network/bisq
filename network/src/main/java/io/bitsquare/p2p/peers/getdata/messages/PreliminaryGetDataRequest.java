package io.bitsquare.p2p.peers.getdata.messages;

import io.bitsquare.app.Capabilities;
import io.bitsquare.app.Version;
import io.bitsquare.p2p.messaging.SupportedCapabilitiesMessage;
import io.bitsquare.p2p.network.messages.AnonymousMessage;

import javax.annotation.Nullable;
import java.util.ArrayList;

public final class PreliminaryGetDataRequest implements AnonymousMessage, GetDataRequest, SupportedCapabilitiesMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final int nonce;
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public PreliminaryGetDataRequest(int nonce) {
        this.nonce = nonce;
    }

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public String toString() {
        return "PreliminaryGetDataRequest{" +
                "messageVersion=" + messageVersion +
                ", nonce=" + nonce +
                ", supportedCapabilities=" + supportedCapabilities +
                '}';
    }
}
