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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order in which a chat shows its messages, the time it shows for each and whether it shows a message as one of
 * the peer.
 * <p>
 * The stored list of a chat is in the order in which this node added the messages: own messages when they were
 * written, received messages when they were processed. That order is kept, so no clock of either side can move a
 * message, and a message is never shown before a message of the other side that its author had seen when writing
 * it. Only messages of the peer that follow each other without another message between them are shown in the order
 * of their sender's dates, because they can arrive together or out of order, for example from the mailbox after the
 * node was offline.
 * <p>
 * A received message shows the time it was received, other messages show their date. A received message is always
 * shown as a message of the peer, whatever sender the peer put into it.
 */
public final class ChatMessageOrder {
    private ChatMessageOrder() {
    }

    /**
     * @param isClient true on the side of the client of the chat,
     *                 see {@link bisq.core.support.SupportSession#isClient()}
     */
    public static List<ChatMessage> inDisplayOrder(List<ChatMessage> chatMessages, boolean isClient) {
        List<ChatMessage> inDisplayOrder = new ArrayList<>(chatMessages.size());
        List<ChatMessage> fromPeer = new ArrayList<>();
        for (ChatMessage chatMessage : chatMessages) {
            if (isFromPeer(chatMessage, isClient)) {
                fromPeer.add(chatMessage);
            } else {
                addInSenderOrder(fromPeer, inDisplayOrder);
                inDisplayOrder.add(chatMessage);
            }
        }
        addInSenderOrder(fromPeer, inDisplayOrder);
        return inDisplayOrder;
    }

    public static long getDisplayDate(ChatMessage chatMessage) {
        return isReceived(chatMessage) ? chatMessage.getReceivedDate() : chatMessage.getDate();
    }

    // Sorting is stable, so messages with the same date keep the order of the list
    private static void addInSenderOrder(List<ChatMessage> fromPeer, List<ChatMessage> inDisplayOrder) {
        fromPeer.sort(Comparator.comparingLong(ChatMessage::getDate));
        inDisplayOrder.addAll(fromPeer);
        fromPeer.clear();
    }

    /**
     * Whether the message is one of the peer. A received message always is. A message stored by an older version has
     * no receive time, so its sender tells. A local system message, such as the trade chat rules, may carry either
     * sender flag and is never counted as a message of the peer.
     *
     * @param isClient true on the side of the client of the chat,
     *                 see {@link bisq.core.support.SupportSession#isClient()}
     */
    public static boolean isFromPeer(ChatMessage chatMessage, boolean isClient) {
        return isReceived(chatMessage) ||
                (!chatMessage.isSystemMessage() && chatMessage.isSenderIsTrader() != isClient);
    }

    /**
     * Whether the message is one of the client of the chat: the taker in the trade chat, the trader in a dispute chat.
     * A received message is one of the peer. A message without a receive time, also a system message, tells by its
     * sender flag.
     *
     * @param isClient true on the side of the client of the chat,
     *                 see {@link bisq.core.support.SupportSession#isClient()}
     */
    public static boolean isFromClient(ChatMessage chatMessage, boolean isClient) {
        return isReceived(chatMessage) ? !isClient : chatMessage.isSenderIsTrader();
    }

    /**
     * Whether the message is a system message that this node added itself. A received system message is not one, also
     * when it comes from the agent. A message stored by an older version has no receive time and counts as one.
     */
    public static boolean isLocalSystemMessage(ChatMessage chatMessage) {
        return chatMessage.isSystemMessage() && !isReceived(chatMessage);
    }

    private static boolean isReceived(ChatMessage chatMessage) {
        return chatMessage.getReceivedDate() > 0;
    }
}
