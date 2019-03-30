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
import bisq.network.p2p.storage.payload.CapabilityRequiringPayload;

import bisq.common.app.Capabilities;
import bisq.common.app.Capability;
import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBlocksRequest extends NetworkEnvelope implements DirectMessage, CapabilityRequiringPayload {
    private final int fromBlockHeight;
    private final int nonce;

    public GetBlocksRequest(int fromBlockHeight, int nonce) {
        this(fromBlockHeight, nonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBlocksRequest(int fromBlockHeight, int nonce, int messageVersion) {
        super(messageVersion);
        this.fromBlockHeight = fromBlockHeight;
        this.nonce = nonce;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBlocksRequest(PB.GetBlocksRequest.newBuilder()
                        .setFromBlockHeight(fromBlockHeight)
                        .setNonce(nonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBlocksRequest proto, int messageVersion) {
        return new GetBlocksRequest(proto.getFromBlockHeight(), proto.getNonce(), messageVersion);
    }

    @Override
    public Capabilities getRequiredCapabilities() {
        return new Capabilities(Capability.DAO_FULL_NODE);
    }


    @Override
    public String toString() {
        return "GetBlocksRequest{" +
                "\n     fromBlockHeight=" + fromBlockHeight +
                ",\n     nonce=" + nonce +
                "\n} " + super.toString();
    }
}
