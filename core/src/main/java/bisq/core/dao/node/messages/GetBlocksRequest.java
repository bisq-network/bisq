/*
 * This file is part of Bisq.
 *
 * Bisq is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or (at
 * your option) any later version.
 *
 * Bisq is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Affero General Public
 * License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Bisq. If not, see <http://www.gnu.org/licenses/>.
 */

package bisq.core.dao.node.messages;

import bisq.network.p2p.DirectMessage;
import bisq.network.p2p.InitialDataRequest;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.SendersNodeAddressMessage;
import bisq.network.p2p.SupportedCapabilitiesMessage;

import bisq.common.app.Capabilities;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

// TODO We remove CapabilityRequiringPayload as it would cause problems if the lite node connects to a new seed node and
// they have not exchanged capabilities already. We need to improve capability handling the we can re-enable it again.
// As this message is sent any only to seed nodes it does not has any effect. Even if a lite node receives it it will be
// simply ignored.

// This message is sent only to full DAO nodes
@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class GetBlocksRequest extends NetworkEnvelope implements DirectMessage, SendersNodeAddressMessage,
        SupportedCapabilitiesMessage, InitialDataRequest {
    private final int fromBlockHeight;
    private final int nonce;

    // Added after version 1.0.1. Can be null if received from older clients.
    @Nullable
    private final NodeAddress senderNodeAddress;

    // Added after version 1.0.1. Can be null if received from older clients.
    @Nullable
    private final Capabilities supportedCapabilities;

    public GetBlocksRequest(int fromBlockHeight,
                            int nonce,
                            @Nullable NodeAddress senderNodeAddress) {
        this(fromBlockHeight,
                nonce,
                senderNodeAddress,
                Capabilities.app,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBlocksRequest(int fromBlockHeight,
                             int nonce,
                             @Nullable NodeAddress senderNodeAddress,
                             @Nullable Capabilities supportedCapabilities,
                             int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
        this.nonce = nonce;
        this.senderNodeAddress = senderNodeAddress;
        this.supportedCapabilities = supportedCapabilities;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.GetBlocksRequest.Builder builder = protobuf.GetBlocksRequest.newBuilder()
                .setFromBlockHeight(fromBlockHeight)
                .setNonce(nonce);
        Optional.ofNullable(senderNodeAddress).ifPresent(e -> builder.setSenderNodeAddress(e.toProtoMessage()));
        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));
        return getNetworkEnvelopeBuilder().setGetBlocksRequest(builder).build();
    }

    public static NetworkEnvelope fromProto(protobuf.GetBlocksRequest proto, int messageVersion) {
        protobuf.NodeAddress protoNodeAddress = proto.getSenderNodeAddress();
        NodeAddress senderNodeAddress = protoNodeAddress.getHostName().isEmpty() ?
                null :
                NodeAddress.fromProto(protoNodeAddress);
        Capabilities supportedCapabilities = proto.getSupportedCapabilitiesList().isEmpty() ?
                null :
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList());
        return new GetBlocksRequest(proto.getFromBlockHeight(),
                proto.getNonce(),
                senderNodeAddress,
                supportedCapabilities,
                messageVersion);
    }

//    @Override
//    public Capabilities getRequiredCapabilities() {
//        return new Capabilities(Capability.DAO_FULL_NODE);
//    }

    @Override
    public String toString() {
        return "GetBlocksRequest{" +
                "\n     fromBlockHeight=" + fromBlockHeight +
                ",\n     nonce=" + nonce +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     supportedCapabilities=" + supportedCapabilities +
                "\n} " + super.toString();
    }
}
