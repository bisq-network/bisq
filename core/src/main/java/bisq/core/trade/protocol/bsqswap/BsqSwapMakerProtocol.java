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

package bisq.core.trade.protocol.bsqswap;


import bisq.core.trade.messages.TradeMessage;
import bisq.core.trade.messages.bsqswap.CreateAtomicTxRequest;
import bisq.core.trade.model.bsqswap.BsqSwapMakerTrade;
import bisq.core.trade.model.bsqswap.BsqSwapTrade;
import bisq.core.trade.protocol.TradeProtocol;
import bisq.core.trade.protocol.TradeTaskRunner;
import bisq.core.trade.protocol.bsqswap.tasks.AtomicApplyFilter;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerCreatesAndSignsTx;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerRemovesOpenOffer;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerSetupTxListener;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerVerifiesAmounts;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerVerifiesMiningFee;
import bisq.core.trade.protocol.bsqswap.tasks.maker.AtomicMakerVerifiesTakerInputs;

import bisq.network.p2p.NodeAddress;

import bisq.common.handlers.ErrorMessageHandler;

public class BsqSwapMakerProtocol extends TradeProtocol {

    private final BsqSwapMakerTrade bsqSwapMakerTrade;

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Constructor
    ///////////////////////////////////////////////////////////////////////////////////////////

    public BsqSwapMakerProtocol(BsqSwapMakerTrade trade) {
        super(trade);

        this.bsqSwapMakerTrade = trade;
    }

    ///////////////////////////////////////////////////////////////////////////////////////////
    // Start trade
    ///////////////////////////////////////////////////////////////////////////////////////////

    public void handleTakeAtomicRequest(CreateAtomicTxRequest message,
                                        NodeAddress sender,
                                        ErrorMessageHandler errorMessageHandler) {
        expect(preCondition(BsqSwapTrade.State.PREPARATION == bsqSwapMakerTrade.getState())
                .with(message)
                .from(sender))
                .setup(tasks(
                        AtomicApplyFilter.class,
                        AtomicMakerVerifiesMiningFee.class,
                        AtomicMakerVerifiesAmounts.class,
                        AtomicMakerVerifiesTakerInputs.class,
                        AtomicMakerRemovesOpenOffer.class,
                        AtomicMakerCreatesAndSignsTx.class,
                        AtomicMakerSetupTxListener.class)
                        .using(new TradeTaskRunner(bsqSwapMakerTrade,
                                () -> handleTaskRunnerSuccess(message),
                                errorMessage -> {
                                    errorMessageHandler.handleErrorMessage(errorMessage);
                                    handleTaskRunnerFault(message, errorMessage);
                                }))
                        .withTimeout(60))
                .executeTasks();
    }

    @Override
    protected void onTradeMessage(TradeMessage message, NodeAddress peer) {
        // Nothing expected
    }
}
