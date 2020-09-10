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

package bisq.core.trade.protocol;

import bisq.core.trade.Trade;
import bisq.core.trade.messages.TradeMessage;

import bisq.network.p2p.NodeAddress;

public abstract class CancelTradeProtocol {
    protected final Trade trade;

    protected CancelTradeProtocol(Trade trade) {
        this.trade = trade;
    }

    abstract void doHandleDecryptedMessage(TradeMessage tradeMessage, NodeAddress sender);

    abstract void doApplyMailboxTradeMessage(TradeMessage tradeMessage, NodeAddress peerNodeAddress);


    ///////////////////////////////////////////////////////////////////////////////////////////
    // Delegates
    ///////////////////////////////////////////////////////////////////////////////////////////

    protected void handleTaskRunnerSuccess(TradeMessage tradeMessage,
                                           String info) {
        trade.getTradeProtocol().handleTaskRunnerSuccess(tradeMessage, info);
    }

    protected void handleTaskRunnerSuccess(String info) {
        trade.getTradeProtocol().handleTaskRunnerSuccess(info);
    }

    protected void handleTaskRunnerFault(TradeMessage tradeMessage, String errorMessage) {
        trade.getTradeProtocol().handleTaskRunnerFault(tradeMessage, errorMessage);
    }

    protected void handleTaskRunnerFault(String errorMessage) {
        trade.getTradeProtocol().handleTaskRunnerFault(errorMessage);
    }
}
