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

package bisq.core.trade.atomic.protocol;


import bisq.core.trade.atomic.AtomicMakerTrade;
import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.atomic.messages.CreateAtomicTxRequest;
import bisq.core.trade.atomic.protocol.tasks.AtomicApplyFilter;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerCreatesAndSignsTx;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerRemovesOpenOffer;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerSetupTxListener;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerVerifiesAmounts;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerVerifiesMiningFee;
import bisq.core.trade.atomic.protocol.tasks.maker.AtomicMakerVerifiesTakerInputs;
import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.TradeTaskRunner;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

public class AtomicMakerProtocol extends TradeProtocol {

    private final AtomicMakerTrade atomicMakerTrade;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public AtomicMakerProtocol(AtomicMakerTrade trade) {
        super(trade);

        this.atomicMakerTrade = trade;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handleTakeAtomicRequest(CreateAtomicTxRequest tradeMessage,
                                        NodeAddress sender,
                                        ErrorMessageHandler errorMessageHandler) {
        expect(preCondition(AtomicTrade.State.PREPARATION == atomicMakerTrade.getState())
                .with(tradeMessage)
                .from(sender))
                .setup(tasks(
                        AtomicApplyFilter.class,
                        AtomicMakerVerifiesMiningFee.class,
                        AtomicMakerVerifiesAmounts.class,
                        AtomicMakerVerifiesTakerInputs.class,
                        AtomicMakerRemovesOpenOffer.class,
                        AtomicMakerCreatesAndSignsTx.class,
                        AtomicMakerSetupTxListener.class)
                        .using(new TradeTaskRunner(atomicMakerTrade,
                                () -> handleTaskRunnerSuccess(tradeMessage),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(tradeMessage, errorMessage);
                                }))
                        .withTimeout(60))
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
    }
}
