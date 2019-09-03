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
import bisq.core.support.SupportManager;
import bisq.core.support.SupportSession;
import bisq.core.support.SupportType;
import bisq.core.support.dispute.messages.DisputeResultMessage;
import bisq.core.support.messages.ChatMessage;
import bisq.core.support.messages.SupportMessage;
import bisq.core.trade.Trade;
import bisq.core.trade.TradeManager;

import bisq.network.p2p.NodeAddress;
import bisq.network.p2p.P2PService;

import javax.inject.Inject;
import javax.inject.Singleton;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Singleton
public class TraderChatManager extends SupportManager {
    private final TradeManager tradeManager;

    public interface DisputeStateListener {
        void onDisputeClosed(String tradeId);
    }

    // Needed to avoid ConcurrentModificationException as we remove a listener at the handler call
    private List<DisputeStateListener> disputeStateListeners = new CopyOnWriteArrayList<>();


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Inject
    public TraderChatManager(P2PService p2PService, WalletsSetup walletsSetup, TradeManager tradeManager) {
        super(p2PService, walletsSetup);
        this.tradeManager = tradeManager;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Implement template methods
    ///////////////////////////////////////////////////////////////////////////////////////////

    @Override
    public SupportType getSupportType() {
        return SupportType.MEDIATION;
    }

    @Override
    public void persist() {
        tradeManager.persistTrades();
    }

    @Override
    public NodeAddress getPeerNodeAddress(ChatMessage message, SupportSession supportSession) {
        Optional<Trade> tradeOptional = tradeManager.getTradeById(message.getTradeId());
        if (tradeOptional.isPresent()) {
            Trade t = tradeOptional.get();
            if (t.getContract() != null)
                return supportSession.isBuyer() ?
                        t.getContract().getSellerNodeAddress() :
                        t.getContract().getBuyerNodeAddress();
        }
        return null;
    }


    ///////////////////////////////////////////////////////////////////////////////////////////
    // API
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void addDisputeStateListener(TraderChatManager.DisputeStateListener disputeStateListener) {
        disputeStateListeners.add(disputeStateListener);
    }

    public void removeDisputeStateListener(TraderChatManager.DisputeStateListener disputeStateListener) {
        disputeStateListeners.remove(disputeStateListener);
    }

    public void dispatchMessage(SupportMessage message) {
        log.info("Received {} with tradeId {} and uid {}",
                message.getClass().getSimpleName(), message.getTradeId(), message.getUid());
        if (message.getSupportType() == SupportType.ARBITRATION) {
            if (message instanceof ChatMessage) {
                if (((ChatMessage) message).getSupportType() == SupportType.TRADE) {
                    onChatMessage((ChatMessage) message);
                }
                // We ignore dispute messages
            } else if (message instanceof DisputeResultMessage) {
                // We notify about dispute closed state
                disputeStateListeners.forEach(e -> e.onDisputeClosed(message.getTradeId()));
            } else {
                log.warn("Unsupported message at dispatchMessage. message={}", message);
            }
        }
    }
}
