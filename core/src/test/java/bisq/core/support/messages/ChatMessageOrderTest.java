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


package bisq.core.support.messages;

import bisq.core.support.SupportType;

import bisq.network.p2p.NodeAddress;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

// The lists are given in the stored order: own messages when they were written, received ones when they were received.
// Unless a test says otherwise, we are the taker in a trade chat, so our own messages have senderIsTrader set.
class ChatMessageOrderTest {
    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1);
    private static final long NOW = 1_700_000_000_000L;
    private static final boolean TAKER = true;
    private static final boolean AGENT = false;
    private static final NodeAddress NODE_ADDRESS = new NodeAddress("localhost", 9999);

    @Test
    void withClocksInSyncMessagesAreShownInTheOrderTheyWereWritten() {
        ChatMessage question = own("question", NOW);
        ChatMessage answer = received("answer", NOW + 30_000, NOW + 31_000);
        ChatMessage nextQuestion = own("next question", NOW + MINUTE);
        ChatMessage nextAnswer = received("next answer", NOW + 90_000, NOW + 91_000);

        assertOrder(List.of(question, answer, nextQuestion, nextAnswer), question, answer, nextQuestion, nextAnswer);
    }

    // The peer's clock is ten minutes ahead, so by date the answer would follow the next question
    @Test
    void aPeerClockAheadDoesNotMoveTheAnswerAfterTheNextQuestion() {
        ChatMessage question = own("question", NOW);
        ChatMessage answer = received("answer", NOW + 10 * MINUTE + 30_000, NOW + 30_000);
        ChatMessage nextQuestion = own("next question", NOW + MINUTE);

        assertOrder(List.of(question, answer, nextQuestion), question, answer, nextQuestion);
    }

    // The peer's clock is ten minutes behind, so by date the answer would come before the question
    @Test
    void aPeerClockBehindDoesNotMoveTheAnswerBeforeTheQuestion() {
        ChatMessage question = own("question", NOW);
        ChatMessage answer = received("answer", NOW - 10 * MINUTE + 30_000, NOW + 30_000);
        ChatMessage nextQuestion = own("next question", NOW + MINUTE);

        assertOrder(List.of(question, answer, nextQuestion), question, answer, nextQuestion);
    }

    // The peer's clock was ten minutes ahead and the peer set it right, so its later messages have earlier dates
    @Test
    void aPeerClockSetBackDoesNotMoveItsLaterMessagesAboveOwnOnes() {
        ChatMessage beforeTheChange = received("before the change", NOW + 10 * MINUTE, NOW);
        ChatMessage question = own("question", NOW + MINUTE);
        ChatMessage answer = received("answer", NOW + 2 * MINUTE, NOW + 2 * MINUTE);
        ChatMessage nextQuestion = own("next question", NOW + 3 * MINUTE);

        assertOrder(List.of(beforeTheChange, question, answer, nextQuestion),
                beforeTheChange, question, answer, nextQuestion);
    }

    // Our clock was ten minutes ahead and was set right between our two messages
    @Test
    void anOwnClockSetBackDoesNotMoveOwnMessages() {
        ChatMessage question = own("question", NOW + 10 * MINUTE);
        ChatMessage answer = received("answer", NOW + 30_000, NOW + 30_000);
        ChatMessage nextQuestion = own("next question", NOW + MINUTE);

        assertOrder(List.of(question, answer, nextQuestion), question, answer, nextQuestion);
    }

    // We sent a message and went offline. At startup the mailbox gave us the peer's two messages in another order.
    @Test
    void messagesFromTheMailboxAfterBeingOfflineAreShownInTheOrderOfTheSender() {
        ChatMessage own = own("own", NOW);
        ChatMessage second = received("B", NOW + 2 * MINUTE, NOW + 60 * MINUTE);
        ChatMessage first = received("A", NOW + MINUTE, NOW + 60 * MINUTE + 5);
        ChatMessage reply = own("reply", NOW + 61 * MINUTE);

        assertOrder(List.of(own, second, first, reply), own, first, second, reply);
    }

    // At startup the messages that arrived directly are processed before the ones from the mailbox
    @Test
    void aDirectMessageProcessedBeforeTheMailboxAtStartupFollowsTheOlderMessages() {
        ChatMessage own = own("own", NOW);
        ChatMessage direct = received("C, direct", NOW + 61 * MINUTE, NOW + 62 * MINUTE);
        ChatMessage first = received("A, mailbox", NOW + MINUTE, NOW + 62 * MINUTE + 5);
        ChatMessage second = received("B, mailbox", NOW + 2 * MINUTE, NOW + 62 * MINUTE + 10);

        assertOrder(List.of(own, direct, first, second), own, first, second, direct);
    }

    // The peer sent A and then B. B arrived directly and we replied, then A arrived from the mailbox. It is shown where
    // it arrived, with the time it arrived.
    @Test
    void aMessageThatArrivesAfterAnOwnReplyIsShownWhereItArrived() {
        ChatMessage second = received("B", NOW + MINUTE, NOW + MINUTE + 10_000);
        ChatMessage reply = own("reply to B", NOW + 2 * MINUTE);
        ChatMessage first = received("A", NOW, NOW + 3 * MINUTE);

        assertOrder(List.of(second, reply, first), second, reply, first);
    }

    @Test
    void aPeerCannotMoveAMessageAcrossOwnMessagesByItsDate() {
        ChatMessage question = own("question", NOW);
        ChatMessage backdated = received("backdated", NOW - TimeUnit.DAYS.toMillis(1), NOW + MINUTE);
        ChatMessage nextQuestion = own("next question", NOW + 2 * MINUTE);
        ChatMessage futureDated = received("future dated", NOW + TimeUnit.DAYS.toMillis(1), NOW + 3 * MINUTE);
        ChatMessage lastQuestion = own("last question", NOW + 4 * MINUTE);

        assertOrder(List.of(question, backdated, nextQuestion, futureDated, lastQuestion),
                question, backdated, nextQuestion, futureDated, lastQuestion);
    }

    // Update in the middle of a chat with a peer ten minutes ahead. The older version stored the first answer when it
    // arrived, without receive time, and it keeps that place.
    @Test
    void afterAnUpgradeAMessageStoredByAnOlderVersionKeepsItsPlace() {
        ChatMessage question = own("Q1", NOW);
        ChatMessage storedAnswer = receivedByAnOlderVersion("A1", NOW + 10 * MINUTE + 30_000);
        ChatMessage secondQuestion = own("Q2", NOW + MINUTE);
        ChatMessage thirdQuestion = own("Q3", NOW + 3 * MINUTE);
        ChatMessage answer = received("A3", NOW + 13 * MINUTE + 30_000, NOW + 3 * MINUTE + 30_000);
        ChatMessage fourthQuestion = own("Q4", NOW + 4 * MINUTE);

        assertOrder(List.of(question, storedAnswer, secondQuestion, thirdQuestion, answer, fourthQuestion),
                question, storedAnswer, secondQuestion, thirdQuestion, answer, fourthQuestion);
    }

    @Test
    void messagesFromTheMailboxStoredByAnOlderVersionAreShownInTheOrderOfTheSender() {
        ChatMessage own = own("own", NOW);
        ChatMessage second = receivedByAnOlderVersion("B", NOW + 2 * MINUTE);
        ChatMessage first = receivedByAnOlderVersion("A", NOW + MINUTE);

        assertOrder(List.of(own, second, first), own, first, second);
    }

    // The trade chat rules message has the trade date and is never ordered with the peer's messages, whatever the
    // sender flag it carries on the taker's side
    @Test
    void theTradeChatRulesMessageStaysFirst() {
        ChatMessage rules = rules(NOW);
        ChatMessage answer = received("answer", NOW - 30 * MINUTE, NOW + MINUTE);
        ChatMessage storedAnswer = receivedByAnOlderVersion("stored answer", NOW - 40 * MINUTE);

        assertOrder(List.of(rules, storedAnswer, answer), rules, storedAnswer, answer);
    }

    // The agent's price info message is created by the agent's node, so the trader's messages before and after it are
    // not ordered together
    @Test
    void aLocalSystemMessageSeparatesThePeerMessagesAroundIt() {
        ChatMessage before = message("before", !AGENT, NOW + 2 * MINUTE);
        before.setReceivedDate(NOW);
        ChatMessage priceInfo = message("price info", AGENT, NOW + MINUTE);
        priceInfo.setSystemMessage(true);
        ChatMessage after = message("after", !AGENT, NOW);
        after.setReceivedDate(NOW + 2 * MINUTE);

        assertOrder(AGENT, List.of(before, priceInfo, after), before, priceInfo, after);
    }

    // On the agent's side the trader is the peer, and the agent's clock is ten minutes behind the trader's
    @Test
    void onTheSideOfTheAgentTheMessagesOfTheTraderAreThePeerMessages() {
        ChatMessage second = message("B from the trader", !AGENT, NOW + 12 * MINUTE);
        ChatMessage first = message("A from the trader", !AGENT, NOW + 11 * MINUTE);
        ChatMessage agentMessage = message("agent", AGENT, NOW + 2 * MINUTE);

        assertOrder(AGENT, List.of(second, first, agentMessage), first, second, agentMessage);
    }

    // A system message of the trader, such as the message about a change of the disputed trade, arrives like a chat
    // message and is ordered with the trader's other messages
    @Test
    void aReceivedSystemMessageIsOrderedWithTheOtherMessagesOfThePeer() {
        ChatMessage agentMessage = message("agent", AGENT, NOW);
        ChatMessage traderMessage = message("trader", !AGENT, NOW + 2 * MINUTE);
        traderMessage.setReceivedDate(NOW + 60 * MINUTE);
        ChatMessage tradeUpdate = message("trade update", !AGENT, NOW + MINUTE);
        tradeUpdate.setSystemMessage(true);
        tradeUpdate.setReceivedDate(NOW + 60 * MINUTE + 5);

        assertOrder(AGENT, List.of(agentMessage, traderMessage, tradeUpdate), agentMessage, tradeUpdate, traderMessage);
    }

    // Our clock was set back between the two receipts, so the receive times are not in the stored order either
    @Test
    void equalDatesKeepTheStoredOrder() {
        ChatMessage own = own("own", NOW);
        ChatMessage second = received("B", NOW + MINUTE, NOW + MINUTE);
        ChatMessage first = received("A", NOW + MINUTE, NOW + MINUTE - 5);

        assertOrder(List.of(own, second, first), own, second, first);
    }

    @Test
    void aReceivedMessageShowsItsReceiveTimeAndAnOwnMessageItsDate() {
        ChatMessage answer = received("answer", NOW + 10 * MINUTE, NOW + 30_000);
        ChatMessage question = own("question", NOW);

        assertEquals(NOW + 30_000, ChatMessageOrder.getDisplayDate(answer));
        assertEquals(NOW, ChatMessageOrder.getDisplayDate(question));
    }

    private static void assertOrder(List<ChatMessage> stored, ChatMessage... expected) {
        assertOrder(TAKER, stored, expected);
    }

    private static void assertOrder(boolean isClient, List<ChatMessage> stored, ChatMessage... expected) {
        assertEquals(texts(List.of(expected)), texts(ChatMessageOrder.inDisplayOrder(stored, isClient)));
    }

    private static List<String> texts(List<ChatMessage> chatMessages) {
        return chatMessages.stream().map(ChatMessage::getMessage).collect(Collectors.toList());
    }

    private static ChatMessage own(String text, long date) {
        return message(text, TAKER, date);
    }

    private static ChatMessage received(String text, long senderDate, long receivedDate) {
        ChatMessage chatMessage = receivedByAnOlderVersion(text, senderDate);
        chatMessage.setReceivedDate(receivedDate);
        return chatMessage;
    }

    private static ChatMessage receivedByAnOlderVersion(String text, long senderDate) {
        return message(text, !TAKER, senderDate);
    }

    // As TraderChatManager.addSystemMsg creates it
    private static ChatMessage rules(long tradeDate) {
        ChatMessage chatMessage = new ChatMessage(SupportType.TRADE, "tradeId", 0, false, "rules",
                new NodeAddress("null:0000"), tradeDate);
        chatMessage.setSystemMessage(true);
        return chatMessage;
    }

    private static ChatMessage message(String text, boolean senderIsTrader, long date) {
        return new ChatMessage(SupportType.TRADE, "tradeId", 0, senderIsTrader, text, NODE_ADDRESS, date);
    }
}
