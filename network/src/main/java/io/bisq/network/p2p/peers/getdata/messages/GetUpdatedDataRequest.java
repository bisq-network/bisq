package io.bisq.network.p2p.peers.getdata.messages;

import com.google.protobuf.ByteString;
import io.bisq.common.app.Version;
import io.bisq.common.proto.ProtoUtil;
import io.bisq.generated.protobuffer.PB;
import io.bisq.network.p2p.NodeAddress;
import io.bisq.network.p2p.SendersNodeAddressMessage;
import lombok.EqualsAndHashCode;
import lombok.Value;

import java.util.Set;
import java.util.stream.Collectors;

import static com.google.common.base.Preconditions.checkNotNull;

@EqualsAndHashCode(callSuper = true)
@Value
public final class GetUpdatedDataRequest extends GetDataRequest implements SendersNodeAddressMessage {
    private final NodeAddress senderNodeAddress;

    public GetUpdatedDataRequest(NodeAddress senderNodeAddress,
                                 int nonce,
                                 Set<byte[]> excludedKeys) {
        this(senderNodeAddress,
                nonce,
                excludedKeys,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetUpdatedDataRequest(NodeAddress senderNodeAddress,
                                  int nonce,
                                  Set<byte[]> excludedKeys,
                                  int messageVersion) {
        super(messageVersion,
                nonce,
                excludedKeys);
        checkNotNull(senderNodeAddress, "senderNodeAddress must not be null at GetUpdatedDataRequest");
        this.senderNodeAddress = senderNodeAddress;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        final PB.GetUpdatedDataRequest.Builder builder = PB.GetUpdatedDataRequest.newBuilder()
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setNonce(nonce)
                .addAllExcludedKeys(excludedKeys.stream()
                        .map(ByteString::copyFrom)
                        .collect(Collectors.toList()));

        return getNetworkEnvelopeBuilder()
                .setGetUpdatedDataRequest(builder)
                .build();
    }

    public static GetUpdatedDataRequest fromProto(PB.GetUpdatedDataRequest proto, int messageVersion) {
        return new GetUpdatedDataRequest(NodeAddress.fromProto(proto.getSenderNodeAddress()),
                proto.getNonce(),
                ProtoUtil.byteSetFromProtoByteStringList(proto.getExcludedKeysList()),
                messageVersion);
    }
}
