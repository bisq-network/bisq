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


import bisq.core.trade.messages.CreateAtomicTxResponse;
import bisq.core.trade.protocol.tasks.PublishTradeStatistics;
import bisq.core.trade.protocol.tasks.taker.AtomicTakerPublishesAtomicTx;
import bisq.core.trade.protocol.tasks.taker.AtomicTakerSetupTxListener;
import bisq.core.trade.protocol.tasks.taker.AtomicTakerVerifiesAtomicTx;

import bisq.network.p2p.NodeAddress;

public interface AtomicTakerProtocol {
    default void handle(CreateAtomicTxResponse tradeMessage, NodeAddress peerNodeAddress) {
        ((TradeProtocol) this).getLog().debug("handle CreateAtomicTxResponse called");
        ((TradeProtocol) this).processModel.setTradeMessage(tradeMessage);
        ((TradeProtocol) this).processModel.setTempTradingPeerNodeAddress(peerNodeAddress);

        TradeTaskRunner taskRunner = new TradeTaskRunner(((TradeProtocol) this).trade,
                () -> ((TradeProtocol) this).handleTaskRunnerSuccess(tradeMessage, "handle CreateAtomicTxResponse"),
                errorMessage -> ((TradeProtocol) this).handleTaskRunnerFault(tradeMessage, errorMessage));

        taskRunner.addTasks(
                AtomicTakerVerifiesAtomicTx.class,
                AtomicTakerPublishesAtomicTx.class,
                AtomicTakerSetupTxListener.class,
                PublishTradeStatistics.class
        );
        taskRunner.run();
    }
}
