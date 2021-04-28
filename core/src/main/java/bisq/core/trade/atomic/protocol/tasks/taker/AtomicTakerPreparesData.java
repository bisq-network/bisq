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

package bisq.core.trade.atomic.protocol.tasks.taker;

import bisq.core.trade.atomic.AtomicTrade;
import bisq.core.trade.protocol.tasks.AtomicTradeTask;

import bisq.common.taskrunner.TaskRunner;

import lombok.extern.slf4j.Slf4j;

import static com.google.common.base.Preconditions.checkArgument;

@Slf4j
public class AtomicTakerPreparesData extends AtomicTradeTask {

    @SuppressWarnings({"unused"})
    public AtomicTakerPreparesData(TaskRunner<AtomicTrade> taskHandler, AtomicTrade atomicTrade) {
        super(taskHandler, atomicTrade);
    }

    @Override
    protected void run() {
        try {
            runInterceptHook();

            checkArgument(!atomicProcessModel.getOffer().isMyOffer(atomicProcessModel.getKeyRing()), "must not take own offer");

            atomicProcessModel.initFromTrade(atomicTrade);
            atomicProcessModel.setTakerBsqAddress(
                    atomicProcessModel.getBsqWalletService().getUnusedAddress().toString());
            atomicProcessModel.setTakerBtcAddress(
                    atomicProcessModel.getBtcWalletService().getFreshAddressEntry().getAddressString());

            atomicProcessModel.initTxBuilder(
                    false,
                    this::complete,
                    (String errorMessage, Throwable throwable) -> {
                        if (throwable != null) {
                            failed(throwable);
                        } else if (!errorMessage.isEmpty()) {
                            failed(errorMessage);
                        } else {
                            failed();
                        }
                    });

        } catch (Throwable t) {
            failed(t);
        }
    }
}
