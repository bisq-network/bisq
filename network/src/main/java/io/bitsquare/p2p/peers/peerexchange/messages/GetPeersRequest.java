package io.bitsquare.p2p.peers.peerexchange.messages;

import io.bitsquare.app.Version;
import io.bitsquare.p2p.NodeAddress;
import io.bitsquare.p2p.network.messages.SendersNodeAddressMessage;
import io.bitsquare.p2p.peers.peerexchange.Peer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashSet;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetPeersRequest extends PeerExchangeMessage implements SendersNodeAddressMessage {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;
    @Nullable
    private ArrayList<Integer> supportedCapabilities = Version.getCapabilities();

    @Override
    @Nullable
    public ArrayList<Integer> getSupportedCapabilities() {
        return supportedCapabilities;
    }

    private final NodeAddress senderNodeAddress;
    public final int nonce;
    public final HashSet<Peer> reportedPeers;

    public GetPeersRequest(NodeAddress senderNodeAddress, int nonce, HashSet<Peer> reportedPeers) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetPeersRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public String toString() {
        return "GetPeersRequest{" +
                "senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                ", reportedPeers.size()=" + reportedPeers.size() +
                "} " + super.toString();
    }
}
