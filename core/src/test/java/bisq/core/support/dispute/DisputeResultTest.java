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

package bisq.core.support.dispute;

import bisq.core.support.SupportType;
import bisq.core.support.messages.ChatMessage;

import bisq.network.p2p.NodeAddress;

import com.google.protobuf.InvalidProtocolBufferException;

import org.bitcoinj.core.Coin;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// The chat message of a result shows that the agent sent the result, so reading a stored result must not create one.
// The payout of a sent mediation result must not change.
class DisputeResultTest {
    private static final String TRADE_ID = "trade-id";
    private static final int TRADER_ID = 1;

    @Test
    void preparedResultHasNoChatMessageAfterReload() throws InvalidProtocolBufferException {
        protobuf.DisputeResult storedResult = preparedResult().toProtoMessage();

        DisputeResult reloadedResult = reload(storedResult);

        assertFalse(storedResult.hasChatMessage());
        assertNull(reloadedResult.getChatMessage());
        assertEquals(70_000, reloadedResult.getBuyerPayoutAmount().value);
        assertEquals(40_000, reloadedResult.getSellerPayoutAmount().value);
    }

    @Test
    void emptyChatMessageStoredByEarlierVersionsIsAbsent() throws InvalidProtocolBufferException {
        protobuf.DisputeResult storedResult = preparedResult().toProtoMessage().toBuilder()
                .setChatMessage(emptyChatMessageOfEarlierVersions())
                .build();

        assertTrue(storedResult.hasChatMessage());
        assertNull(reload(storedResult).getChatMessage());
    }

    @Test
    void sentResultKeepsItsChatMessageAfterReload() throws InvalidProtocolBufferException {
        DisputeResult sentResult = preparedResult();
        ChatMessage chatMessage = new ChatMessage(SupportType.MEDIATION,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                new NodeAddress("mediator.onion", 9999));
        sentResult.setChatMessage(chatMessage);

        ChatMessage reloadedChatMessage = reload(sentResult.toProtoMessage()).getChatMessage();

        assertNotNull(reloadedChatMessage);
        assertEquals(chatMessage.getUid(), reloadedChatMessage.getUid());
        assertEquals("result", reloadedChatMessage.getMessage());
    }

    @Test
    void payoutOfSentMediationResultCannotChange() {
        DisputeResult result = sentResult(SupportType.MEDIATION);

        assertThrows(IllegalStateException.class, () -> result.setBuyerPayoutAmount(Coin.valueOf(80_000)));
        assertThrows(IllegalStateException.class, () -> result.setSellerPayoutAmount(Coin.valueOf(30_000)));

        assertEquals(70_000, result.getBuyerPayoutAmount().value);
        assertEquals(40_000, result.getSellerPayoutAmount().value);
    }

    @Test
    void payoutOfSentMediationResultCannotChangeAfterReload() throws InvalidProtocolBufferException {
        DisputeResult result = reload(sentResult(SupportType.MEDIATION).toProtoMessage());

        assertThrows(IllegalStateException.class, () -> result.setBuyerPayoutAmount(Coin.valueOf(80_000)));
        assertEquals(70_000, result.getBuyerPayoutAmount().value);
    }

    // The mediator's window sets the payout of a locked result again with the same amounts
    @Test
    void sentMediationResultAcceptsItsOwnPayout() {
        DisputeResult result = sentResult(SupportType.MEDIATION);

        result.setBuyerPayoutAmount(Coin.valueOf(70_000));
        result.setSellerPayoutAmount(Coin.valueOf(40_000));

        assertEquals(70_000, result.getBuyerPayoutAmount().value);
        assertEquals(40_000, result.getSellerPayoutAmount().value);
    }

    @Test
    void payoutOfPreparedResultCanChange() {
        DisputeResult result = preparedResult();

        result.setBuyerPayoutAmount(Coin.valueOf(80_000));
        result.setSellerPayoutAmount(Coin.valueOf(30_000));

        assertFalse(result.isSent());
        assertEquals(80_000, result.getBuyerPayoutAmount().value);
        assertEquals(30_000, result.getSellerPayoutAmount().value);
    }

    @Test
    void payoutOfSentRefundResultCanChange() {
        DisputeResult result = sentResult(SupportType.REFUND);

        result.setBuyerPayoutAmount(Coin.valueOf(80_000));
        result.setSellerPayoutAmount(Coin.valueOf(30_000));

        assertTrue(result.isSent());
        assertEquals(80_000, result.getBuyerPayoutAmount().value);
        assertEquals(30_000, result.getSellerPayoutAmount().value);
    }

    // Earlier versions read an absent chat message as the default instance and stored it with the result again
    private static protobuf.ChatMessage emptyChatMessageOfEarlierVersions() {
        return ChatMessage.fromPayloadProto(protobuf.ChatMessage.getDefaultInstance())
                .toProtoNetworkEnvelope()
                .getChatMessage();
    }

    private static DisputeResult reload(protobuf.DisputeResult storedResult) throws InvalidProtocolBufferException {
        return DisputeResult.fromProto(protobuf.DisputeResult.parseFrom(storedResult.toByteArray()));
    }

    private static DisputeResult preparedResult() {
        DisputeResult result = new DisputeResult(TRADE_ID, TRADER_ID);
        result.setBuyerPayoutAmount(Coin.valueOf(70_000));
        result.setSellerPayoutAmount(Coin.valueOf(40_000));
        return result;
    }

    private static DisputeResult sentResult(SupportType supportType) {
        DisputeResult result = preparedResult();
        result.setChatMessage(new ChatMessage(supportType,
                TRADE_ID,
                TRADER_ID,
                false,
                "result",
                new NodeAddress("agent.onion", 9999)));
        return result;
    }
}
