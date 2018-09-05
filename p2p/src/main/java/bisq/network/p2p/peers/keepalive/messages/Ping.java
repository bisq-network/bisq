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

package bisq.network.p2p.peers.keepalive.messages;

import bisq.common.app.Version;
import bisq.common.proto.network.NetworkEnvelope;

import io.bisq.generated.protobuffer.PB;

import lombok.EqualsAndHashCode;
import lombok.Value;

@EqualsAndHashCode(callSuper = true)
@Value
public final class Ping extends NetworkEnvelope implements KeepAliveMessage {
    private final int nonce;
    private final int lastRoundTripTime;

    public Ping(int nonce, int lastRoundTripTime) {
        this(nonce, lastRoundTripTime, Version.getP2PMessageVersion());
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private Ping(int nonce, int lastRoundTripTime, int messageVersion) {
        super(messageVersion);
        this.nonce = nonce;
        this.lastRoundTripTime = lastRoundTripTime;
    }

    @Override
    public PB.NetworkEnvelope toProtoNetworkEnvelope() {
        return getNetworkEnvelopeBuilder()
                .setPing(PB.Ping.newBuilder()
                        .setNonce(nonce)
                        .setLastRoundTripTime(lastRoundTripTime))
                .build();
    }

    public static Ping fromProto(PB.Ping proto, int messageVersion) {
        return new Ping(proto.getNonce(), proto.getLastRoundTripTime(), messageVersion);
    }
}
