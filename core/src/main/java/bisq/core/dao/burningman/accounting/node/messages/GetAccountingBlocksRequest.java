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

package bisq.core.dao.burningman.accounting.node.messages;

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

// Taken from GetBlocksRequest
@EqualsAndHashCode(callSuper = true)
@Getter
@Slf4j
public final class GetAccountingBlocksRequest extends NetworkEnvelope implements DirectMessage, SendersNodeAddressMessage,
        SupportedCapabilitiesMessage, InitialDataRequest {
    private final int fromBlockHeight;
    private final int nonce;

    private final NodeAddress senderNodeAddress;
    private final Capabilities supportedCapabilities;

    public GetAccountingBlocksRequest(int fromBlockHeight,
                                      int nonce,
                                      NodeAddress senderNodeAddress) {
        this(fromBlockHeight,
                nonce,
                senderNodeAddress,
                Capabilities.app,
                Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetAccountingBlocksRequest(int fromBlockHeight,
                                       int nonce,
                                       NodeAddress senderNodeAddress,
                                       Capabilities supportedCapabilities,
                                       int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
        this.nonce = nonce;
        this.senderNodeAddress = senderNodeAddress;
        this.supportedCapabilities = supportedCapabilities;
    }

    //todo
    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        protobuf.GetAccountingBlocksRequest.Builder builder = protobuf.GetAccountingBlocksRequest.newBuilder()
                .setFromBlockHeight(fromBlockHeight)
                .setNonce(nonce);
        Optional.ofNullable(senderNodeAddress).ifPresent(e -> builder.setSenderNodeAddress(e.toProtoMessage()));
        Optional.ofNullable(supportedCapabilities).ifPresent(e -> builder.addAllSupportedCapabilities(Capabilities.toIntList(supportedCapabilities)));
        return getNetworkEnvelopeBuilder().setGetAccountingBlocksRequest(builder).build();
    }

    public static NetworkEnvelope fromProto(protobuf.GetAccountingBlocksRequest proto, int messageVersion) {
        protobuf.NodeAddress protoNodeAddress = proto.getSenderNodeAddress();
        NodeAddress senderNodeAddress = protoNodeAddress.getHostName().isEmpty() ?
                null :
                NodeAddress.fromProto(protoNodeAddress);
        Capabilities supportedCapabilities = proto.getSupportedCapabilitiesList().isEmpty() ?
                null :
                Capabilities.fromIntList(proto.getSupportedCapabilitiesList());
        return new GetAccountingBlocksRequest(proto.getFromBlockHeight(),
                proto.getNonce(),
                senderNodeAddress,
                supportedCapabilities,
                messageVersion);
    }


    @Override
    public String toString() {
        return "GetAccountingBlocksRequest{" +
                "\n     fromBlockHeight=" + fromBlockHeight +
                ",\n     nonce=" + nonce +
                ",\n     senderNodeAddress=" + senderNodeAddress +
                ",\n     supportedCapabilities=" + supportedCapabilities +
                "\n} " + super.toString();
    }
}
