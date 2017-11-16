package io.bisq.network.p2p.peers.peerexchange.messages;

import io.bisq.common.app.Capabilities;
import io.bisq.common.app.Version;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendersNodeAddressMessage;
import io.bisq.network.p2p.SupportedCapabilitiesMessage;
import io.bisq.network.p2p.peers.peerexchange.Peer;
import lombok.EqualsAndHashCode;
import lombok.Value;

import javax.annotation.Nullable;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Value
public final class GetPeersRequest extends NetworkEnvelope implements PeerExchangeMessage, SendersNodeAddressMessage, SupportedCapabilitiesMessage {
    private final NodeAddress senderNodeAddress;
    private final int nonce;
    private final HashSet<Peer> reportedPeers;
    @Nullable
    private final List<Integer> supportedCapabilities;

    public GetPeersRequest(NodeAddress senderNodeAddress, int nonce, HashSet<Peer> reportedPeers) {
        this(senderNodeAddress, nonce, reportedPeers, Capabilities.getSupportedCapabilities(), Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetPeersRequest(NodeAddress senderNodeAddress,
                            int nonce,
                            HashSet<Peer> reportedPeers,
                            @Nullable List<Integer> supportedCapabilities,
                            int messageVersion) {
        super(messageVersion);
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetPeersRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.reportedPeers = reportedPeers;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.GetPeersRequest.Builder builder = PB.GetPeersRequest.newBuilder()
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setNonce(nonce)
                .addAllReportedPeers(reportedPeers.stream()
                        .map(Peer::toProtoMessage)
                        .collect(Collectors.toList()));

        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(supportedCapabilities));

        return getNetworkEnvelopeBuilder()
                .setGetPeersRequest(builder)
                .build();
    }

    public static GetPeersRequest fromProto(PB.GetPeersRequest proto, int messageVersion) {
        return new GetPeersRequest(NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getNonce(),
                new HashSet<>(proto.getReportedPeersList().stream()
                        .map(Peer::fromProto)
                        .collect(Collectors.toSet())),
                proto.getSupportedCapabilitiesList().isEmpty() ? null : proto.getSupportedCapabilitiesList(),
                messageVersion);
    }
}
