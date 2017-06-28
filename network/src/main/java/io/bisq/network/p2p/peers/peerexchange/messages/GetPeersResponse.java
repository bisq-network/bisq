package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.stream.Collectors;

@EqualsAndHashCode(callSuper = true)
@Value
public final class GetPeersResponse extends NetworkEnvelope implements PeerExchangeMessage, SupportedCapabilitiesMessage {
    private final int requestNonce;
    private final HashSet<Peer> reportedPeers;
    private final ArrayList<Integer> supportedCapabilities = Capabilities.getCapabilities();

    public GetPeersResponse(int requestNonce, HashSet<Peer> reportedPeers) {
        this(requestNonce, reportedPeers, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetPeersResponse(int requestNonce, HashSet<Peer> reportedPeers, int messageVersion) {
        super(messageVersion);
        this.requestNonce = requestNonce;
        this.reportedPeers = reportedPeers;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetPeersResponse(PB.GetPeersResponse.newBuilder()
                        .setRequestNonce(requestNonce)
                        .addAllReportedPeers(reportedPeers.stream()
                                .map(Peer::toProtoMessage)
                                .collect(Collectors.toList()))
                        .addAllSupportedCapabilities(supportedCapabilities))
                .build();
    }

    public static GetPeersResponse fromProto(PB.GetPeersResponse getPeersResponse, int messageVersion) {
        HashSet<Peer> reportedPeers = new HashSet<>(
                getPeersResponse.getReportedPeersList()
                        .stream()
                        .map(peer -> new Peer(new NodeAddress(peer.getNodeAddress().getHostName(),
                                peer.getNodeAddress().getPort())))
                        .collect(Collectors.toList()));
        return new GetPeersResponse(getPeersResponse.getRequestNonce(), reportedPeers, messageVersion);
    }
}
