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

package bisq.core.trade;

import bisq.core.arbitration.messages.DisputeCommunicationMessage;
import bisq.core.arbitration.messages.DisputeMessage;
import bisq.core.chat.ChatManager;
import bisq.core.chat.ChatSession;

import bisq.network.p2p.NodeAddress;

import bisq.common.crypto.PubKeyRing;

import javafx.collections.ObservableList;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;

/* Makers are considered as servers and takers as clients for trader to trader chat
 * sessions. This is only to make it easier to understand who's who, there is no real
 * server/client relationship */
public class TradeChatSession extends ChatSession {
    private static final Logger log = LoggerFactory.getLogger(TradeChatSession.class);

    @Nullable
    private Trade trade;
    private boolean isClient;
    private boolean isBuyer;
    private TradeManager tradeManager;
    private ChatManager chatManager;

    public TradeChatSession(@Nullable Trade trade,
                            boolean isClient,
                            boolean isBuyer,
                            TradeManager tradeManager,
                            ChatManager chatManager) {
        super(DisputeCommunicationMessage.Type.TRADE);
        this.trade = trade;
        this.isClient = isClient;
        this.isBuyer = isBuyer;
        this.tradeManager = tradeManager;
        this.chatManager = chatManager;
    }

    @Override
    public boolean isClient() {
        return isClient;
    }

    @Override
    public String getTradeId() {
        return trade.getId();
    }

    @Override
    public PubKeyRing getClientPubKeyRing() {
        // Get pubkeyring of taker. Maker is considered server for chat sessions
        if (trade.getContract() != null)
            return trade.getContract().getTakerPubKeyRing();
        return null;
    }

    @Override
    public void addDisputeCommunicationMessage(DisputeCommunicationMessage message) {
        trade.addCommunicationMessage(message);
    }

    @Override
    public void persist() {
        tradeManager.persistTrades();
    }

    @Override
    public ObservableList<DisputeCommunicationMessage> getDisputeCommunicationMessages() {
        return trade.getCommunicationMessages();
    }

    @Override
    public boolean chatIsOpen() {
        return trade.getState() != Trade.State.WITHDRAW_COMPLETED;
    }

    @Override
    public NodeAddress getPeerNodeAddress(DisputeCommunicationMessage message) {
        Optional<Trade> tradeOptional = tradeManager.getTradeById(message.getTradeId());
        if (tradeOptional.isPresent()) {
            Trade t = tradeOptional.get();
            if (t.getContract() != null)
                return isBuyer ?
                        t.getContract().getSellerNodeAddress() :
                        t.getContract().getBuyerNodeAddress();
        }
        return null;
    }

    @Override
    public PubKeyRing getPeerPubKeyRing(DisputeCommunicationMessage message) {
        Optional<Trade> tradeOptional = tradeManager.getTradeById(message.getTradeId());
        if (tradeOptional.isPresent()) {
            Trade t = tradeOptional.get();
            if (t.getContract() != null && t.getOffer() != null) {
                if (t.getOffer().getOwnerPubKey().equals(tradeManager.getKeyRing().getPubKeyRing().getSignaturePubKey())) {
                    // I am maker
                    return t.getContract().getTakerPubKeyRing();
                } else {
                    return t.getContract().getMakerPubKeyRing();
                }
            }
        }
        return null;
    }

    @Override
    public void dispatchMessage(DisputeMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());
        if (message instanceof DisputeCommunicationMessage) {
            if (((DisputeCommunicationMessage) message).getType() != DisputeCommunicationMessage.Type.TRADE) {
                log.debug("Ignore non trade type communication message");
                return;
            }
            chatManager.onDisputeDirectMessage((DisputeCommunicationMessage) message);
        } else {
            log.warn("Unsupported message at dispatchMessage.\nmessage=" + message);
        }
    }

    @Override
    public List<DisputeCommunicationMessage> getChatMessages() {
        return tradeManager.getTradableList().stream()
                .flatMap(trade -> trade.getCommunicationMessages().stream())
                .collect(Collectors.toList());
    }

    @Override
    public boolean channelOpen(DisputeCommunicationMessage message) {
        return tradeManager.getTradeById(message.getTradeId()).isPresent();
    }

    @Override
    public void storeDisputeCommunicationMessage(DisputeCommunicationMessage message) {
        Optional<Trade> tradeOptional = tradeManager.getTradeById(message.getTradeId());
        if (tradeOptional.isPresent()) {
            if (tradeOptional.get().getCommunicationMessages().stream()
                    .noneMatch(m -> m.getUid().equals(message.getUid()))) {
                tradeOptional.get().addCommunicationMessage(message);
            } else {
                log.warn("Trade got a disputeCommunicationMessage what we have already stored. UId = {} TradeId = {}",
                        message.getUid(), message.getTradeId());
            }
        }
    }
}
