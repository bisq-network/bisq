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

package bisq.core.trade.messages;

import bisq.core.account.sign.SignedWitness;

import bisq.network.p2p.MailboxMessage;
import bisq.network.p2p.NodeAddress;

import lombok.EqualsAndHashCode;
import lombok.Value;

// Not used anymore from v.1.3.9 on. We need to keep it for backward compatibility.
// Now we send the signedWitness as part of the PayoutTxPublishedMessage to avoid that we send 2 messages and need to
// process 2 messages at the receiver. Parallel/concurrent handling of multiple messages per trade is not supported by
// the design of the trade protocol and should be avoided.
@Deprecated
@EqualsAndHashCode(callSuper = true)
@Value
public class TraderSignedWitnessMessage extends TradeMessage implements MailboxMessage {
    private final NodeAddress senderNodeAddress;
    private final SignedWitness signedWitness;


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
}
