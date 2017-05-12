package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendersNodeAddressMessage;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {

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
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder().setGetUpdatedDataRequest(
                PB.GetUpdatedDataRequest.newBuilder()
                        .setMessageVersion(messageVersion)
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setNonce(nonce)
                        .addAllExcludedKeys(excludedKeys.stream()
                                .map(ByteString::copyFrom).collect(Collectors.toList()))).build();
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
