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

package bisq.core.support.traderchat;

import bisq.core.btc.setup.WalletsSetup;
import bisq.core.locale.Res;
import bisq.core.support.SupportManager;
import bisq.core.support.SupportType;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;

import bisq.network.p2p.AckMessageSourceType;
import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import bisq.common.crypto.PubKeyRing;

import javax.inject.Inject;
import javax.inject.Singleton;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TraderChatManager extends SupportManager {
    private final TradeManager tradeManager;
    private final PubKeyRing pubKeyRing;


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TraderChatManager(P2PService p2PService,
                             WalletsSetup walletsSetup,
                             TradeManager tradeManager,
                             PubKeyRing pubKeyRing) {
        super(p2PService, walletsSetup);
        this.tradeManager = tradeManager;
        this.pubKeyRing = pubKeyRing;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.TRADE;
    }

    @Override
    public void persist() {
        tradeManager.persistTrades();
    }

    @Override
    public NodeAddress getPeerNodeAddress(ChatMessage message) {
        return tradeManager.getTradeById(message.getTradeId()).map(trade -> {
            if (trade.getContract() != null) {
                return trade.getContract().getPeersNodeAddress(pubKeyRing);
            } else {
                return null;
            }
        }).orElse(null);
    }

    @Override
    public PubKeyRing getPeerPubKeyRing(ChatMessage message) {
        return tradeManager.getTradeById(message.getTradeId()).map(trade -> {
            if (trade.getContract() != null) {
                return trade.getContract().getPeersPubKeyRing(pubKeyRing);
            } else {
                return null;
            }
        }).orElse(null);
    }

    @Override
    public List<ChatMessage> getAllChatMessages() {
        return tradeManager.getTradableList().stream()
                .flatMap(trade -> trade.getChatMessages().stream())
                .collect(Collectors.toList());
    }

    @Override
    public boolean channelOpen(ChatMessage message) {
        return tradeManager.getTradeById(message.getTradeId()).isPresent();
    }

    @Override
    public void addAndPersistChatMessage(ChatMessage message) {
        tradeManager.getTradeById(message.getTradeId()).ifPresent(trade -> {
            ObservableList<ChatMessage> chatMessages = trade.getChatMessages();
            if (chatMessages.stream().noneMatch(m -> m.getUid().equals(message.getUid()))) {
                if (chatMessages.isEmpty()) {
                    addSystemMsg(trade);
                }
                trade.addAndPersistChatMessage(message);
            } else {
                log.warn("Trade got a chatMessage that we have already stored. UId = {} TradeId = {}",
                        message.getUid(), message.getTradeId());
            }
        });
    }

    @Override
    protected AckMessageSourceType getAckMessageSourceType() {
        return AckMessageSourceType.TRADE_CHAT_MESSAGE;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void dispatchMessage(SupportMessage message) {
        if (canProcessMessage(message)) {
            log.info("Received {} with tradeId {} and uid {}",
                    message.getClass().getSimpleName(), message.getTradeId(), message.getUid());
            if (message instanceof ChatMessage) {
                onChatMessage((ChatMessage) message);
            } else {
                log.warn("Unsupported message at dispatchMessage. message={}", message);
            }
        }
    }

    public void addSystemMsg(Trade trade) {
        // We need to use the trade date as otherwise our system msg would not be displayed first as the list is sorted
        // by date.
        ChatMessage chatMessage = new ChatMessage(
                getSupportType(),
                trade.getId(),
                0,
                false,
                Res.get("tradeChat.rules"),
                new NodeAddress("null:0000"),
                trade.getDate().getTime());
        chatMessage.setSystemMessage(true);
        trade.getChatMessages().add(chatMessage);
    }
}
