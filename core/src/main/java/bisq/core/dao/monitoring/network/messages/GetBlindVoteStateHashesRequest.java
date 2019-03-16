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

package bisq.core.dao.monitoring.network.messages;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Getter;

@EqualsAndHashCode(callSuper = true)
@Getter
public final class GetBlindVoteStateHashesRequest extends GetStateHashesRequest {
    public GetBlindVoteStateHashesRequest(int fromCycleStartHeight, int nonce) {
        super(fromCycleStartHeight, nonce, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private GetBlindVoteStateHashesRequest(int height, int nonce, int messageVersion) {
        super(height, nonce, messageVersion);
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setGetBlindVoteStateHashesRequest(PB.GetBlindVoteStateHashesRequest.newBuilder()
                        .setHeight(height)
                        .setNonce(nonce))
                .build();
    }

    public static NetworkEnvelope fromProto(PB.GetBlindVoteStateHashesRequest proto, int messageVersion) {
        return new GetBlindVoteStateHashesRequest(proto.getHeight(), proto.getNonce(), messageVersion);
    }
}
