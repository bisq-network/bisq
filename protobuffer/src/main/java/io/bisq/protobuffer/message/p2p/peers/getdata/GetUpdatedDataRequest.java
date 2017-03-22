package io.bisq.protobuffer.message.p2p.peers.getdata;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.generated.protobuffer.PB;
import io.bisq.protobuffer.message.SendersNodeAddressMessage;
import io.bisq.protobuffer.payload.p2p.NodeAddress;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {
    // That object is sent over the wire, so we need to take care of version compatibility.
    private static final long serialVersionUID = Version.P2P_NETWORK_VERSION;

    private final int messageVersion = Version.getP2PMessageVersion();
    private final NodeAddress senderNodeAddress;
    private final int nonce;
    private final Set<byte[]> excludedKeys;

    public GetUpdatedDataRequest(NodeAddress senderNodeAddress, int nonce, Set<byte[]> excludedKeys) {
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetUpdatedDataRequest");
        this.senderNodeAddress = senderNodeAddress;
        this.nonce = nonce;
        this.excludedKeys = excludedKeys;
    }

    @Override
    public int getNonce() {
        return nonce;
    }

    @Override
    public NodeAddress getSenderNodeAddress() {
        return senderNodeAddress;
    }

    @Override
    public Set<byte[]> getExcludedKeys() {
        return excludedKeys;
    }

    @Override
    public int getMessageVersion() {
        return messageVersion;
    }

    @Override
    public PB.Envelope toProto() {
        return PB.Envelope.newBuilder().setP2PNetworkVersion(Version.P2P_NETWORK_VERSION)
                .setGetUpdatedDataRequest(
                        PB.GetUpdatedDataRequest.newBuilder()
                                .setMessageVersion(messageVersion)
                                .setSenderNodeAddress(senderNodeAddress.toProto())
                                .setNonce(nonce)
                                .addAllExcludedKeys(excludedKeys.stream()
                                        .map(bytes -> ByteString.copyFrom(bytes)).collect(Collectors.toList()))).build();
    }

    @Override
    public String toString() {
        return "GetUpdatedDataRequest{" +
                "senderNodeAddress=" + senderNodeAddress +
                ", nonce=" + nonce +
                ", messageVersion=" + messageVersion +
                '}';
    }
}
