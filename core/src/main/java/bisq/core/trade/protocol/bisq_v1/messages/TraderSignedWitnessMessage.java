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

package bisq.core.trade.protocol.bisq_v1.messages;

import bisq.core.account.sign.SignedWitness;

import bisq.network.p2p.NodeAddress;

import bisq.common.app.Version;

import lombok.EqualsAndHashCode;
import lombok.Value;

/**
 * Not used anymore since v1.4.0
 */
@Deprecated
@SuppressWarnings("ALL")
@EqualsAndHashCode(callSuper = true)
@Value
public class TraderSignedWitnessMessage extends TradeMailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final SignedWitness signedWitness;

    public TraderSignedWitnessMessage(String uid,
                                      String tradeId,
                                      NodeAddress senderNodeAddress,
                                      SignedWitness signedWitness) {
        this(Version.getP2PMessageVersion(),
                uid,
                tradeId,
                senderNodeAddress,
                signedWitness);
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // PROTO BUFFER
    ///////////////////////////////////////////////////////////////////////////////////////////

    private TraderSignedWitnessMessage(int messageVersion,
                                       String uid,
                                       String tradeId,
                                       NodeAddress senderNodeAddress,
                                       SignedWitness signedWitness) {
        super(messageVersion, tradeId, uid);
        this.senderNodeAddress = senderNodeAddress;
        this.signedWitness = signedWitness;
    }

    @Override
    public protobuf.NetworkEnvelope toProtoNetworkEnvelope() {
        final protobuf.TraderSignedWitnessMessage.Builder builder = protobuf.TraderSignedWitnessMessage.newBuilder();
        builder.setUid(uid)
                .setTradeId(tradeId)
                .setSenderNodeAddress(senderNodeAddress.toProtoMessage())
                .setSignedWitness(signedWitness.toProtoSignedWitness());
        return getNetworkEnvelopeBuilder().setTraderSignedWitnessMessage(builder).build();
    }

    public static TraderSignedWitnessMessage fromProto(protobuf.TraderSignedWitnessMessage proto, int messageVersion) {
        return new TraderSignedWitnessMessage(messageVersion,
                proto.getUid(),
                proto.getTradeId(),
                NodeAddress.fromProto(proto.getSenderNodeAddress()),
                SignedWitness.fromProto(proto.getSignedWitness()));
    }

    @Override
    public String toString() {
        return "TraderSignedWitnessMessage{" +
                "\n     senderNodeAddress=" + senderNodeAddress +
                "\n     signedWitness=" + signedWitness +
                "\n} " + super.toString();
    }

}
