package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.common.proto.network.NetworkEnvelope;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendersNodeAddressMessage;
import lombok.Value;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@Value
public final class GetUpdatedDataRequest implements SendersNodeAddressMessage, GetDataRequest {
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
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return NetworkEnvelope.getDefaultBuilder()
                .setGetUpdatedDataRequest(PB.GetUpdatedDataRequest.newBuilder()
                        .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                        .setNonce(nonce)
                        .addAllExcludedKeys(excludedKeys.stream()
                                .map(ByteString::copyFrom)
                                .collect(Collectors.toList())))
                .build();
    }

    public static GetUpdatedDataRequest fromProto(PB.GetUpdatedDataRequest getUpdatedDataRequest) {
        return new GetUpdatedDataRequest(NodeAddress.fromProto(getUpdatedDataRequest.getSenderNodeAddress()),
                getUpdatedDataRequest.getNonce(),
                ProtoUtil.getByteSet(getUpdatedDataRequest.getExcludedKeysList()));
    }
}
