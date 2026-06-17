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

package bisq.core.support.dispute.messages;

import bisq.core.support.SupportType;
import bisq.core.support.dispute.DisputeResult;
import bisq.core.support.messages.ChatMessage;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.Sig;

import java.security.PublicKey;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DisputeResultMessageTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 1;
    private static final NodeAddress SENDER_NODE_ADDRESS = new NodeAddress("peer.onion", 9999);

    @Test
    public void toProtoAndFromProtoPreservesSenderSignaturePubKey() {
        PublicKey senderSignaturePubKey = Sig.generateKeyPair().getPublic();
        DisputeResultMessage message = new DisputeResultMessage(disputeResult(),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION,
                senderSignaturePubKey);

        protobuf.DisputeResultMessage proto = message.toProtoNetworkEnvelope().getDisputeResultMessage();
        DisputeResultMessage fromProto = DisputeResultMessage.fromProto(proto, 1);
        PublicKey parsedSenderSignaturePubKey = fromProto.getSenderSignaturePubKey();

        assertFalse(proto.getSenderSignaturePubKey().isEmpty());
        assertNotNull(parsedSenderSignaturePubKey);
        assertArrayEquals(Sig.getPublicKeyBytes(senderSignaturePubKey),
                Sig.getPublicKeyBytes(parsedSenderSignaturePubKey));
    }

    @Test
    public void fromProtoKeepsMissingSenderSignaturePubKeyAsNull() {
        protobuf.DisputeResultMessage proto = protobuf.DisputeResultMessage.newBuilder()
                .setUid("uid")
                .setDisputeResult(disputeResult().toProtoMessage())
                .setSenderNodeAddress(SENDER_NODE_ADDRESS.toProtoMessage())
                .setType(SupportType.toProtoMessage(SupportType.MEDIATION))
                .build();

        DisputeResultMessage fromProto = DisputeResultMessage.fromProto(proto, 1);

        assertNull(fromProto.getSenderSignaturePubKey());
    }

    @Test
    public void senderSignaturePubKeyIsAdvisoryForDisputeResultMessage() {
        DisputeResultMessage message = new DisputeResultMessage(disputeResult(),
                SENDER_NODE_ADDRESS,
                "uid",
                SupportType.MEDIATION,
                null);

        assertFalse(message.isSenderSignaturePubKeyRequired());
    }

    private static DisputeResult disputeResult() {
        ChatMessage chatMessage = new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                SENDER_NODE_ADDRESS);
        DisputeResult disputeResult = new DisputeResult(TRADE_ID, TRADER_ID);
        disputeResult.setChatMessage(chatMessage);
        return disputeResult;
    }
}
